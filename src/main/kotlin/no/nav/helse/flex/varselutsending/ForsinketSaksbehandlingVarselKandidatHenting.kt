package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.vedtaksperiodebehandling.PeriodeStatusRepository
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ForsinketSaksbehandlingVarselKandidatHenting(
    private val environmentToggles: EnvironmentToggles,
    private val periodeStatusRepository: PeriodeStatusRepository,
    private val manglendeInntektsmeldingVarsling: ManglendeInntektsmeldingVarsling,
) {
    private val log = logger()

    fun finnOgProsseserKandidater(now: OffsetDateTime): Map<String, Int> {
        val sendtFoer =
            if (environmentToggles.isDevGcp()) {
                now.minusMinutes(4).toInstant()
            } else {
                now.minusDays(28).toInstant()
            }

        val fnr =
            periodeStatusRepository.finnPersonerMedForsinketSaksbehandlingGrunnetManglendeInntektsmelding(sendtFoer = sendtFoer)
                .map { it.fnr }.distinct()

        val returMap = mutableMapOf<String, Int>()
        log.info("Fant ${fnr.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap["antallUnikeFnrForForsinketSbGrunnetManlendeInntektsmelding"] = fnr.size

        fnr.forEach {
            /*     val resultat =
                     manglendeInntektsmeldingVarsling.prosseserManglendeInntektsmeldingKandidat(it, sendtFoer)

                 if (returMap.containsKey(resultat)) {
                     returMap[resultat] = returMap[resultat]!! + 1
                 } else {
                     returMap[resultat] = 1
                 }

             */
        }

        return returMap
    }
}
