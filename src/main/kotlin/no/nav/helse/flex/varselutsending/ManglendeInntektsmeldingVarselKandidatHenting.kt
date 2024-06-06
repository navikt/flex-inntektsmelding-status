package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.vedtaksperiodebehandling.PeriodeStatusRepository
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ManglendeInntektsmeldingVarselKandidatHenting(
    private val environmentToggles: EnvironmentToggles,
    private val periodeStatusRepository: PeriodeStatusRepository,
    private val manglendeInntektsmeldingVarsling: ManglendeInntektsmeldingVarsling,
) {
    private val log = logger()

    fun finnOgProsseserKandidater(now: OffsetDateTime = OffsetDateTime.now()): Map<String, Int> {
        val sendtFoer =
            if (environmentToggles.isDevGcp()) {
                now.minusMinutes(2).toInstant()
            } else {
                now.minusDays(15).toInstant()
            }

        val kandidater =
            periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtFoer = sendtFoer)

        val unikeFnr = kandidater.map { it.fnr }.distinct()
        unikeFnr.forEach {
            manglendeInntektsmeldingVarsling.prosseserManglendeInntektsmeldingKandidat(it, sendtFoer)
        }
        log.info("Fant ${kandidater.size} kandidater for varselutsending for manglende inntektsmelding")
        log.info("Fant ${unikeFnr.size} unike fnr for varselutsending for manglende inntektsmelding")
        return mapOf(
            "antallKandidaterInntektsmeldingVarsling" to kandidater.size,
            "antallUnikeFnrInntektsmeldingVarsling" to unikeFnr.size,
        )
    }
}
