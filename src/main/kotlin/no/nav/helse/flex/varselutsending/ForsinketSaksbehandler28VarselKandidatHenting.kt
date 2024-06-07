package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.increment
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ForsinketSaksbehandler28VarselKandidatHenting(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forsinketSaksbehandlingVarsling28: ForsinketSaksbehandlingVarsling28,
) {
    private val log = logger()

    fun hentOgProsseser(now: OffsetDateTime): Map<CronJobStatus, Int> {
        val sendtFoer = now.minusDays(28).toInstant()

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28] = fnrListe.size

        fnrListe.forEach { fnr ->
            forsinketSaksbehandlingVarsling28.prosseserManglendeInntektsmelding28(fnr, sendtFoer)
                .also {
                    returMap.increment(it)
                }
        }

        return returMap
    }
}
