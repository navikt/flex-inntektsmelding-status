package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

const val SLETT_SPEILSTATUSER_BATCH_STORRELSE = 5000

@Component
class SlettSpeilstatuserJob(
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
) {
    private val log = logger()

    @Scheduled(
        initialDelay = 5,
        fixedDelay = 10,
        timeUnit = TimeUnit.MINUTES,
    )
    fun run(): Int {
        log.info("Starter ${this::class.simpleName}")

        var totaltSlettet = 0
        while (true) {
            val antallSlettet = vedtaksperiodeBehandlingStatusRepository.slettSpeilstatuserBatch(SLETT_SPEILSTATUSER_BATCH_STORRELSE)
            totaltSlettet += antallSlettet
            if (antallSlettet > 0) {
                log.info("Slettet $antallSlettet speilstatuser, totalt $totaltSlettet så langt")
            }
            if (antallSlettet < SLETT_SPEILSTATUSER_BATCH_STORRELSE) {
                break
            }
        }

        log.info("${this::class.simpleName} ferdig. Slettet totalt $totaltSlettet speilstatuser")
        return totaltSlettet
    }
}
