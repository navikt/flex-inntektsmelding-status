package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

@Component
class VarselutsendingCronJob(
    private val manglendeInntektsmeldingVarselKandidatHenting: ManglendeInntektsmeldingVarselKandidatHenting,
) {
    private val log = logger()

    @Scheduled(initialDelay = 1, fixedDelay = 120, timeUnit = TimeUnit.MINUTES)
    fun run(): HashMap<String, Int> {
        return runMedParameter(OffsetDateTime.now())
    }

    fun runMedParameter(now: OffsetDateTime): HashMap<String, Int> {
        log.info("Starter VarselutsendingCronJob")
        val resultat = HashMap<String, Int>()
        val tid =
            measureTime {
                manglendeInntektsmeldingVarselKandidatHenting.finnOgProsseserKandidater(now).also { resultat.putAll(it) }
            }
        log.info("manglendeInntektsmeldingVarselKandidatHenting kjørte på $tid")
        log.info("Resultat fra VarselutsendingCronJob: $resultat")
        return resultat
    }
}
