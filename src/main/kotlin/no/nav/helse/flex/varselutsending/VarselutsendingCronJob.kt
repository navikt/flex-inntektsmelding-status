package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class VarselutsendingCronJob(
    private val manglendeInntektsmelding15VarselKandidatHenting: ManglendeInntektsmelding15VarselKandidatHenting,
    private val manglendeInntektsmelding28VarselKandidatHenting: ManglendeInntektsmelding28VarselKandidatHenting,
    private val forsinketSaksbehandler28VarselKandidatHenting: ForsinketSaksbehandler28VarselKandidatHenting,
) {
    private val log = logger()

    @Scheduled(initialDelay = 1, fixedDelay = 120, timeUnit = TimeUnit.MINUTES)
    fun run(): Map<CronJobStatus, Int> {
        if (OffsetDateTime.now().dayOfWeek in setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)) {
            log.info("Det er helg, jobben kjøres ikke")
            return emptyMap()
        }
        if (OffsetDateTime.now().hour < 9 || OffsetDateTime.now().hour > 15) {
            log.info("Det er ikke dagtid, jobben kjøres ikke")
            return emptyMap()
        }

        return runMedParameter(OffsetDateTime.now())
    }

    fun runMedParameter(now: OffsetDateTime): Map<CronJobStatus, Int> {
        log.info("Starter VarselutsendingCronJob")
        val resultat = HashMap<CronJobStatus, Int>()

        manglendeInntektsmelding15VarselKandidatHenting.hentOgProsseser(now).also { resultat.putAll(it) }
        manglendeInntektsmelding28VarselKandidatHenting.hentOgProsseser(now).also { resultat.putAll(it) }
        forsinketSaksbehandler28VarselKandidatHenting.hentOgProsseser(now).also { resultat.putAll(it) }

        log.info("Resultat fra VarselutsendingCronJob: $resultat")
        return resultat
    }
}

enum class CronJobStatus {
    SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15,
    UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15,
    UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_28,
    MANGLENDE_INNTEKTSMELDING_VARSEL_15_DISABLET_I_PROD,
    FLERE_PERIODER_IKKE_IMPLEMENTERT,
    MANGLENDE_INNTEKTSMELDING_VARSEL_28_DISABLET_I_PROD,
    SENDT_VARSEL_MANGLER_INNTEKTSMELDING_28,
    UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28,
    FORSINKET_SAKSBEHANDLING_VARSEL_28_DISABLET_I_PROD,
    SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28,
    FORVENTET_EN_INNTEKTSMELDING_FANT_IKKE,
    VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
    INGEN_PERIODE_FUNNET_FOR_VARSEL_MANGLER_INNTEKTSMELDING_15,
    INGEN_PERIODE_FUNNET_FOR_VARSEL_MANGLER_INNTEKTSMELDING_18,
    HAR_FATT_NYLIG_VARSEL,
}
