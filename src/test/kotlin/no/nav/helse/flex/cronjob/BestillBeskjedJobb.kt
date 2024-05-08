package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtaksperiode.StatusRepository
import no.nav.helse.flex.vedtaksperiode.StatusVerdi
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime

@Component
class BestillBeskjedJobb(
    private val statusRepository: StatusRepository,
    private val bestillBeskjed: BestillBeskjed,
    @Value("\${INNTEKTSMELDING_MANGLER_VENTETID}") private val ventetid: Long,
) {
    private val log = logger()

    private fun sykmeldtVarsel() = OffsetDateTime.now().minusDays(ventetid).toInstant()

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
