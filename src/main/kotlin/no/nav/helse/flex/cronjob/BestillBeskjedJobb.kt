package no.nav.helse.flex.cronjob

import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar.SATURDAY
import java.util.concurrent.TimeUnit

@Component
class BestillBeskjedJobb(
    private val statusRepository: StatusRepository,
    private val bestillBeskjed: BestillBeskjed,
    @Value("\${INNTEKTSMELDING_MANGLER_VENTETID}") private val ventetid: Long,
) {
    private val log = logger()

    private fun sykmeldtVarsel() = OffsetDateTime.now().minusDays(ventetid).toInstant()

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    fun job() {
        // Returner direkte hvis det er kveldstid eller helg
        if (OffsetDateTime.now().dayOfWeek in setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)) {
            log.info("Det er helg, jobben kjøres ikke")
            return
        }
        if (OffsetDateTime.now().hour < 9 || OffsetDateTime.now().hour > 15) {
            log.info("Det er ikke dagtid, jobben kjøres ikke")
            return
        }
        log.info("Kjører jobb for å bestille beskjeder for inntektsmeldinger som mangler etter $ventetid dager")
        jobMedParameter(opprettetFor = sykmeldtVarsel())
    }

    fun jobMedParameter(opprettetFor: Instant) {
        var beskjederBestilt = 0

        val manglerImEldreEnnXdager =
            statusRepository
                .hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
                .filter { it.statusOpprettet.isBefore(opprettetFor) }
                .sortedByDescending { it.vedtakFom }

        log.info("Fant ${manglerImEldreEnnXdager.size} inntektsmeldinger som mangler etter $ventetid dager")

        manglerImEldreEnnXdager
            .take(200).forEach {
                if (bestillBeskjed.opprettVarsler(it)) {
                    beskjederBestilt++
                }
            }

        if (beskjederBestilt > 0) {
            log.info("Behandlet $beskjederBestilt antall inntektsmeldinger som mangler etter $ventetid dager")
        } else {
            log.info("Ingen inntektsmeldinger som mangler etter $ventetid dager ble behandlet")
        }
    }
}
