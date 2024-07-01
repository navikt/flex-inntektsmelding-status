package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.increment
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ManglendeInntektsmelding15VarselKandidatHenting(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val manglendeInntektsmeldingVarsling15: ManglendeInntektsmeldingVarsling15,
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    private val varselGrense = if (environmentToggles.isProduction()) 200 else 20

    fun hentOgProsseser(now: OffsetDateTime): Map<CronJobStatus, Int> {
        val sendtFoer = now.minusDays(15).toInstant()

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] = fnrListe.size

        fnrListe.forEachIndexed { idx, fnr ->
            manglendeInntektsmeldingVarsling15.prosseserManglendeInntektsmeldingKandidat(fnr, sendtFoer)
                .also {
                    returMap.increment(it)
                }
            val antallSendteVarsler = returMap[CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15]
            if (antallSendteVarsler != null && antallSendteVarsler >= varselGrense) {
                returMap[CronJobStatus.UTELATTE_FNR_MANGLER_IM_15_THROTTLE] = fnrListe.size - idx - 1
                return returMap
            }
        }

        return returMap
    }
}
