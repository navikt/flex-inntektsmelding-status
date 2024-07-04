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
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    private val maxAntallUtsendelsePerKjoring = if (environmentToggles.isProduction()) 250 else 20
    private val funksjonellGrenseForAntallVarsler = if (environmentToggles.isProduction()) 2000 else 50

    fun hentOgProsseser(now: OffsetDateTime): Map<CronJobStatus, Int> {
        val sendtFoer = now.minusDays(15).toInstant()

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] = fnrListe.size

        // dryrun kall
        val antallUtsendinger = fnrListe
            .map { fnr ->
                manglendeInntektsmeldingVarsling15.prosseserManglendeInntektsmeldingKandidat(
                    fnr,
                    sendtFoer,
                    dryRun = true
                )
            }
            .filter { it == CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15 }

        log.info("Dry run for manglende inntektsmelding 15, antall varsler: ${antallUtsendinger.size}")

        if (antallUtsendinger.size >= funksjonellGrenseForAntallVarsler) {
            val melding =
                "Funksjonell grense for antall varsler nådd, antall varsler: ${antallUtsendinger.size}. Grensen er satt til $funksjonellGrenseForAntallVarsler"
            log.error(melding)
            throw RuntimeException(melding)
        }

        fnrListe.forEachIndexed { idx, fnr ->
            manglendeInntektsmeldingVarsling15.prosseserManglendeInntektsmeldingKandidat(fnr, sendtFoer, dryRun = false)
                .also {
                    returMap.increment(it)
                }
            val antallSendteVarsler = returMap[CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15]
            if (antallSendteVarsler != null && antallSendteVarsler >= maxAntallUtsendelsePerKjoring) {
                returMap[CronJobStatus.UTELATTE_FNR_MANGLER_IM_15_THROTTLE] = fnrListe.size - idx - 1
                return returMap
            }
        }

        return returMap
    }
}
