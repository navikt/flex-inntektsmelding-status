package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.LukkMelding
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class MeldingOgBrukervarselDone(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val brukervarsel: Brukervarsel,
    private val meldingKafkaProducer: MeldingKafkaProducer,
) {
    val log = logger()

    @Transactional(propagation = Propagation.MANDATORY)
    fun doneSendteManglerImVarsler(
        vedtaksperiodeBehandling: VedtaksperiodeBehandlingDbRecord,
        fnr: String?,
    ) {
        doneVarselMedStatus(
            vedtaksperiodeBehandling = vedtaksperiodeBehandling,
            statusVerdiSok = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
            statusVerdiDone = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE,
            fnr = fnr,
        )
        doneVarselMedStatus(
            vedtaksperiodeBehandling = vedtaksperiodeBehandling,
            statusVerdiSok = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_ANDRE,
            statusVerdiDone = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_ANDRE_DONE,
            fnr = fnr,
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun doneForsinketSbVarsel(
        vedtaksperiodeBehandling: VedtaksperiodeBehandlingDbRecord,
        fnr: String?,
    ) {
        doneVarselMedStatus(
            vedtaksperiodeBehandling = vedtaksperiodeBehandling,
            statusVerdiSok = StatusVerdi.VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
            statusVerdiDone = StatusVerdi.VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE_DONE,
            fnr = fnr,
        )
        doneVarselMedStatus(
            vedtaksperiodeBehandling = vedtaksperiodeBehandling,
            statusVerdiSok = StatusVerdi.REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
            statusVerdiDone = StatusVerdi.REVARSLET_VENTER_PÅ_SAKSBEHANDLER_DONE,
            fnr = fnr,
        )
    }

    fun doneVarselMedStatus(
        vedtaksperiodeBehandling: VedtaksperiodeBehandlingDbRecord,
        statusVerdiSok: StatusVerdi,
        statusVerdiDone: StatusVerdi,
        fnr: String?,
    ) {
        if (vedtaksperiodeBehandling.sisteVarslingstatus != statusVerdiSok) {
            return
        }
        if (fnr == null) {
            return
        }
        vedtaksperiodeBehandlingStatusRepository.save(
            VedtaksperiodeBehandlingStatusDbRecord(
                vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id!!,
                opprettetDatabase = Instant.now(),
                tidspunkt = Instant.now(),
                status = statusVerdiDone,
                dittSykefravaerMeldingId = null,
                brukervarselId = null,
            ),
        )
        vedtaksperiodeBehandlingRepository.save(
            vedtaksperiodeBehandling.copy(
                sisteVarslingstatus = statusVerdiDone,
                sisteVarslingstatusTidspunkt = Instant.now(),
                oppdatertDatabase = Instant.now(),
            ),
        )
        val varsletManglerImStatus =
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(vedtaksperiodeBehandling.id))
                .firstOrNull { it.status == statusVerdiSok }
                ?: throw RuntimeException("Fant ikke varslet mangler im status, den skal være her")

        brukervarsel.sendDonemelding(
            fnr = fnr,
            bestillingId = varsletManglerImStatus.brukervarselId!!,
        )
        log.info("Donet brukervarsel ${varsletManglerImStatus.brukervarselId}")

        meldingKafkaProducer.produserMelding(
            meldingUuid = varsletManglerImStatus.dittSykefravaerMeldingId!!,
            meldingKafkaDto =
                MeldingKafkaDto(
                    fnr = fnr,
                    lukkMelding =
                        LukkMelding(
                            timestamp = Instant.now(),
                        ),
                ),
        )
        log.info("Donet ditt sykefravær melding ${varsletManglerImStatus.dittSykefravaerMeldingId}")
    }
}
