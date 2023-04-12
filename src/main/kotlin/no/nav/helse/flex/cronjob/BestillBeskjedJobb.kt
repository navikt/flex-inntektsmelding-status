package no.nav.helse.flex.cronjob

import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class BestillBeskjedJobb(
    private val statusRepository: StatusRepository,
    private val bestillBeskjed: BestillBeskjed,
    @Value("\${INNTEKTSMELDING_MANGLER_VENTETID}") private val ventetid: Long
) {

    private val log = logger()

    private fun sykmeldtVarsel() = OffsetDateTime.now().minusDays(ventetid).toInstant()

    @Scheduled(initialDelay = 2, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun job() {
        jobMedParameter(opprettetFor = sykmeldtVarsel())
    }

    fun jobMedParameter(opprettetFor: Instant) {
        var beskjederBestilt = 0

        val manglerBeskjed = statusRepository
            .hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .filter { it.statusOpprettet.isBefore(opprettetFor) }
            .sortedByDescending { it.vedtakFom }
            .take(200)

        manglerBeskjed.forEach {
            if (bestillBeskjed.opprettVarsler(it)) {
                beskjederBestilt++
            }
        }

        if (beskjederBestilt > 0) {
            log.info("Behandlet $beskjederBestilt antall inntektsmeldinger som mangler etter 4 uker")
        }
    }
}
