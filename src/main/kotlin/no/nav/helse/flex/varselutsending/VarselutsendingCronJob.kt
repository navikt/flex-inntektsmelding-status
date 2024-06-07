package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class VarselutsendingCronJob(
    private val manglendeInntektsmelding15VarselKandidatHenting: ManglendeInntektsmelding15VarselKandidatHenting,
    private val manglendeInntektsmelding28VarselKandidatHenting: ManglendeInntektsmelding28VarselKandidatHenting,
) {
    private val log = logger()

    @Scheduled(initialDelay = 1, fixedDelay = 120, timeUnit = TimeUnit.MINUTES)
    fun run(): HashMap<CronJobStatus, Int> {
        return runMedParameter(OffsetDateTime.now())
    }

    fun runMedParameter(now: OffsetDateTime): HashMap<CronJobStatus, Int> {
        log.info("Starter VarselutsendingCronJob")
        val resultat = HashMap<CronJobStatus, Int>()

        manglendeInntektsmelding15VarselKandidatHenting.finnOgProsseserKandidater(now).also { resultat.putAll(it) }
        manglendeInntektsmelding28VarselKandidatHenting.finnOgProsseserKandidater(now).also { resultat.putAll(it) }

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
    TODO_IMPLEMENT,
    SENDT_VARSEL_MANGLER_INNTEKTSMELDING_28,
}
