package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
class FiksVarselEngangsJob(
    private val fiksVarsel: FiksVarsel,
) {
    @Scheduled(initialDelay = 2, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun run() {
        fiksVarsel.run()
    }
}

@Component
class FiksVarsel(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val meldingOgBrukervarselDone: MeldingOgBrukervarselDone,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    private val log = logger()

    @Transactional
    fun run() {
        val behandling =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = "96ffe201-e73f-4f85-80a4-c02515798954",
                behandlingId = "d25e4473-bfb1-43d4-bee1-b4f50d3bb0df",
            )!!

        if (behandling.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_SAKSBEHANDLER) {
            log.info(
                "FiksVarselEngangsJob: Skal oppdatere status for ${behandling.vedtaksperiodeId} og ${behandling.behandlingId} til FERDIG",
            )
            val now = Instant.now()

            val relevanteVedtaksperiodebehandlingSykepengesoknaderRelations =
                vedtaksperiodeBehandlingSykepengesoknadRepository
                    .findByVedtaksperiodeBehandlingId(
                        behandling.id!!,
                    ).first()

            val relevanteSykepengesoknader =
                sykepengesoknadRepository.findBySykepengesoknadUuid(
                    relevanteVedtaksperiodebehandlingSykepengesoknaderRelations.sykepengesoknadUuid,
                )!!

            meldingOgBrukervarselDone.doneForsinketSbVarsel(behandling, relevanteSykepengesoknader.fnr)
            log.info("FiksVarselEngangsJob: Varsel done for ${behandling.vedtaksperiodeId} og ${behandling.behandlingId}")

            val lagretBehandling =
                vedtaksperiodeBehandlingRepository.save(
                    behandling.copy(
                        sisteSpleisstatus = StatusVerdi.FERDIG,
                        sisteSpleisstatusTidspunkt = now,
                        oppdatertDatabase = now,
                    ),
                )
            log.info("FiksVarselEngangsJob: ${lagretBehandling.vedtaksperiodeId} og ${lagretBehandling.behandlingId} satt til FERDIG")
        }
    }
}
