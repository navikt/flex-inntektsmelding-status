package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.varseltekst.skapForelagteOpplysningerTekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class SendForelagteOpplysningerCronjob(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val hentAltForPerson: HentAltForPerson,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    private val log = logger()

    data class RelevantMeldingInfo(
        val vedtaksperiodeBehandlingId: String,
        val sykepengesoknadUuid: String,
        val orgnummer: String,
    )

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
        val tekstViSender = skapForelagteOpplysningerTekst()


        val usendteMeldinger: List<ForelagteOpplysningerDbRecord> = forelagteOpplysningerRepository.findAllByForelagtIsNull()

        val sendteMeldinger: List<ForelagteOpplysningerDbRecord> = forelagteOpplysningerRepository.findAllByForelagtIsNotNull() // todo bør bare gjelde for siste x mnd

        fun finnOrgNrForMelding(melding: ForelagteOpplysningerDbRecord): List<String> {
            val vedtaksperiodeBehandlingId =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId = melding.vedtaksperiodeId,
                    behandlingId = melding.behandlingId,
                )!!.id

            val relevanteVedtaksperiodebehandlingSykepengesoknaderRelations =
                vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingId(
                    vedtaksperiodeBehandlingId!!,
                )

            val relevanteSykepengesoknader =
                sykepengesoknadRepository.findBySykepengesoknadUuidIn(
                    relevanteVedtaksperiodebehandlingSykepengesoknaderRelations.map {
                        it.sykepengesoknadUuid
                    },
                )

            val relevanteOrgnr = relevanteSykepengesoknader.mapNotNull { it.orgnummer }

            return relevanteOrgnr
        }

        for (usendtMelding in usendteMeldinger) {
            val fnr = usendtMelding.fnr
            if (fnr == null) {
                continue
            }
            
            val meldingerTilPerson = forelagteOpplysningerRepository.findByFnrIn(fnr)

            val nyligSendteMeldingerTilPerson =
                meldingerTilPerson.filter { it.opprettet != null }.filter {
                    it.opprettet.isAfter(
                        now.minus(
                            Duration.ofDays(28),
                        ),
                    )
                }

            if (nyligSendteMeldingerTilPerson.isNotEmpty()) {
                val orgnrForUsendtMelding = finnOrgNrForMelding(usendtMelding).firstOrNull()
                val orgnummerForSendtMeldinger = sendteMeldinger.flatMap { finnOrgNrForMelding(it) }

                if (orgnrForUsendtMelding != null && orgnummerForSendtMeldinger.contains(orgnrForUsendtMelding)) {
                    resultat[CronJobStatus.HAR_FATT_NYLIG_VARSEL] = (resultat[CronJobStatus.HAR_FATT_NYLIG_VARSEL] ?: 0) + 1
                } else {
                    // todo send varsel
                }
            } else {
                // todo log no need to check because no recent messages
            }
        }

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

    VARSLER_ALLEREDE_OM_VENTER_PA_SAKSBEHANDLER,
}
