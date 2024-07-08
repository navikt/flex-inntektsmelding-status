package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.tilOsloZone
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class VarselutsendingCronJob(
    private val manglendeInntektsmeldingFørsteVarselFinnPersoner: ManglendeInntektsmeldingFørsteVarselFinnPersoner,
    private val manglendeInntektsmeldingAndreVarselFinnPersoner: ManglendeInntektsmeldingAndreVarselFinnPersoner,
    private val forsinketSaksbehandlingFørsteVarselFinnPersoner: ForsinketSaksbehandlingFørsteVarselFinnPersoner,
    private val forsinketSaksbehandlingRevarselFinnPersoner: ForsinketSaksbehandlingRevarselFinnPersoner,
) {
    private val log = logger()

    @Scheduled(initialDelay = 1, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun run(): Map<CronJobStatus, Int> {
        val osloDatetimeNow = OffsetDateTime.now().tilOsloZone()
        if (osloDatetimeNow.dayOfWeek in setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)) {
            log.info("Det er helg, jobben kjøres ikke")
            return emptyMap()
        }
        if (osloDatetimeNow.hour < 9 || osloDatetimeNow.hour > 15) {
            log.info("Det er ikke dagtid, jobben kjøres ikke")
            return emptyMap()
        }

        return runMedParameter(Instant.now())
    }

    fun runMedParameter(now: OffsetDateTime): Map<CronJobStatus, Int> {
        return runMedParameter(now.toInstant())
    }

    fun runMedParameter(now: Instant): Map<CronJobStatus, Int> {
        log.info("Starter VarselutsendingCronJob")
        val resultat = HashMap<CronJobStatus, Int>()

        manglendeInntektsmeldingFørsteVarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }
        manglendeInntektsmeldingAndreVarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }
        forsinketSaksbehandlingFørsteVarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }
        forsinketSaksbehandlingRevarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }

        log.info(
            "Resultat fra VarselutsendingCronJob: ${
                resultat.map { "${it.key}: ${it.value}" }.sorted().joinToString(
                    separator = "\n",
                    prefix = "\n",
                )
            }",
        )
        return resultat
    }
}

enum class CronJobStatus {
    SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING,
    SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING,
    SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING,
    SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING,

    UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING,
    UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING,
    UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING,

    INGEN_PERIODE_FUNNET_FOR_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL,
    INGEN_PERIODE_FUNNET_FOR_ANDER_MANGLER_INNTEKTSMELDING_VARSEL,
    INGEN_PERIODE_FUNNET_FOR_FØRSTE_FORSINKET_SAKSBEHANDLING_VARSEL,

    THROTTLET_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL,
    THROTTLET_ANDRE_MANGLER_INNTEKTSMELDING_VARSEL,
    THROTTLET_FØRSTE_FORSINKER_SAKSBEHANDLING_VARSEL,
    THROTTLET_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL,

    VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
    FANT_INGEN_INNTEKTSMELDING,

    HAR_FATT_NYLIG_VARSEL,

    FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN,
    ANDRE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN,
    FØRSTE_FORSINKET_SAKSBEHANDLING_VARSEL_DRY_RUN,
    REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL_DRY_RUN,

    FANT_FLERE_ENN_EN_VEDTAKSPERIODE_FOR_REVARSEL,
    INGEN_PERIODE_FUNNET_FOR_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL,
    UNIKE_FNR_KANDIDATER_REVARSEL_FORSINKET_SAKSBEHANDLING,
}
