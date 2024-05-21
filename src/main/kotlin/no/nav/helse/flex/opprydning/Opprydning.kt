
package no.nav.helse.flex.opprydning

import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.LukkMelding
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.vedtaksperiode.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class Opprydning(
    private val statusRepository: StatusRepository,
    private val lockRepository: LockRepository,
    private val brukernotifikasjon: Brukernotifikasjon,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeStatusRepository: VedtaksperiodeStatusRepository,
) {
    private val log = logger()

    @Transactional
    fun fjernVarsler(vedtaksperiodeMedStatus: VedtaksperiodeMedStatus) {
        lockRepository.settAdvisoryTransactionLock(vedtaksperiodeMedStatus.fnr.toLong())

        val dbId = vedtaksperiodeMedStatus.id

        val inntektsmelding =
            statusRepository.hentVedtaksperiodeMedStatusHistorikk(vedtaksperiodeMedStatus.id)!!

        if (inntektsmelding.harBeskjedSendt()) {
            doneBeskjed(inntektsmelding, dbId)
        }

        if (inntektsmelding.harMeldingSendt()) {
            doneMelding(inntektsmelding, dbId)
        }
    }

    private fun doneBeskjed(
        inntektsmelding: VedtaksperiodeMedStatusHistorikk,
        dbId: String,
    ) {
        if (inntektsmelding.harBeskjedDonet()) {
            log.info("Inntektsmelding ${inntektsmelding.eksternId} har allerede donet brukernotifikasjon beskjed")
            return
        }

        val bestillingId =
            inntektsmelding.statusHistorikk.first { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }.id

        brukernotifikasjon.sendDonemelding(
            fnr = inntektsmelding.fnr,
            eksternId = inntektsmelding.eksternId,
            bestillingId = bestillingId,
        )

        vedtaksperiodeStatusRepository.save(
            VedtaksperiodeStatusDbRecord(
                vedtaksperiodeDbId = dbId,
                opprettet = Instant.now(),
                status = StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
            ),
        )

        log.info("Donet brukernotifikasjon beskjed om manglende inntektsmelding ${inntektsmelding.eksternId}")
    }

    private fun doneMelding(
        inntektsmelding: VedtaksperiodeMedStatusHistorikk,
        dbId: String,
    ) {
        if (inntektsmelding.harMeldingDonet()) {
            log.info("Inntektsmelding ${inntektsmelding.eksternId} har allerede donet ditt sykefravær melding")
            return
        }

        val bestillingId =
            inntektsmelding.statusHistorikk.first { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }.id

        meldingKafkaProducer.produserMelding(
            meldingUuid = bestillingId,
            meldingKafkaDto =
                MeldingKafkaDto(
                    fnr = inntektsmelding.fnr,
                    lukkMelding =
                        LukkMelding(
                            timestamp = Instant.now(),
                        ),
                ),
        )

        vedtaksperiodeStatusRepository.save(
            VedtaksperiodeStatusDbRecord(
                vedtaksperiodeDbId = dbId,
                opprettet = Instant.now(),
                status = StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT,
            ),
        )

        log.info("Donet ditt sykefravær melding om manglende inntektsmelding ${inntektsmelding.eksternId}")
    }
}
