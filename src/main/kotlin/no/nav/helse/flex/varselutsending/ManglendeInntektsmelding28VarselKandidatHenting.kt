package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.increment
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ManglendeInntektsmelding28VarselKandidatHenting(
    private val environmentToggles: EnvironmentToggles,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val manglendeInntektsmeldingVarsling28: ManglendeInntektsmeldingVarsling28,
) {
    private val log = logger()

    fun finnOgProsseserKandidater(now: OffsetDateTime): Map<CronJobStatus, Int> {
        val sendtFoer =
            if (environmentToggles.isDevGcp()) {
                now.minusMinutes(4).toInstant()
            } else {
                now.minusDays(28).toInstant()
            }

        val fnrListe =
            vedtaksperiodeBehandlingRepository.finnPersonerMedForsinketSaksbehandlingGrunnetManglendeInntektsmelding(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_28] = fnrListe.size

        fnrListe.forEach { fnr ->
            manglendeInntektsmeldingVarsling28.prosseserManglendeInntektsmeldingKandidat(fnr, sendtFoer)
                .also {
                    returMap.increment(it)
                }
        }

        return returMap
    }
}
