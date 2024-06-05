package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.vedtaksperiodebehandling.PeriodeStatusRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime

@Component
class ManglendeInntektsmeldingVarselKandidatHenting(
    private val environmentToggles: EnvironmentToggles,
    private val periodeStatusRepository: PeriodeStatusRepository,
) {
    private val log = logger()

    fun finnOgProsseserKandidater(now: OffsetDateTime = OffsetDateTime.now()): Map<String, Int> {
        fun sendtFoer(): Instant {
            if (environmentToggles.isDevGcp()) {
                return now.minusMinutes(2).toInstant()
            }
            return now.minusDays(15).toInstant()
        }

        val kandidater =
            periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtFoer = sendtFoer())

        log.info("Fant ${kandidater.size} kandidater for varselutsending for manglende inntektsmelding")

        return mapOf("antallKandidaterInntektsmeldingVarsling" to kandidater.size)
    }
}
