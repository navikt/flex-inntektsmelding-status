package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.increment
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ForsinketSaksbehandler28VarselKandidatHenting(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forsinketSaksbehandlingVarsling28: ForsinketSaksbehandlingVarsling28,
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()
    private val varselGrense = if (environmentToggles.isProduction()) 120 else 10

    fun hentOgProsseser(now: OffsetDateTime): Map<CronJobStatus, Int> {
        val sendtFoer = now.minusDays(28).toInstant()

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28] = fnrListe.size

        fnrListe.forEachIndexed { idx, fnr ->
            forsinketSaksbehandlingVarsling28.prosseserManglendeInntektsmelding28(fnr, sendtFoer)
                .also {
                    returMap.increment(it)
                }

            val antallSendteVarsler = returMap[CronJobStatus.SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28]
            if (antallSendteVarsler != null && antallSendteVarsler >= varselGrense) {
                returMap[CronJobStatus.UTELATTE_FNR_FORSINKET_SAKSBEHANDLING_THROTTLE] = fnrListe.size - idx - 1
                return returMap
            }
        }
        return returMap
    }
}
