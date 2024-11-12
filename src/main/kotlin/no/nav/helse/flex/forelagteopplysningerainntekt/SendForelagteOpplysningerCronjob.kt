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

        //        manglendeInntektsmeldingFørsteVarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }
        //        manglendeInntektsmeldingAndreVarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }
        //        forsinketSaksbehandlingFørsteVarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }
        //        forsinketSaksbehandlingRevarselFinnPersoner.hentOgProsseser(now).also { resultat.putAll(it) }

        val usendteMeldinger: List<ForelagteOpplysningerDbRecord> = forelagteOpplysningerRepository.findAllByForelagtIsNull()

        // val vedtaksperiodeBehandlingId =  vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(vedtaksperiodeId = forelagteOpplysningerRe.vedtaksperiodeId, behandlingId = forelagteOpplysninger.behandlingId)!!.id

        val altForPerson = hentAltForPerson.hentAltForPerson(usendteMeldinger.first().fnr!!)

        val sendteMeldinger: List<ForelagteOpplysningerDbRecord> = forelagteOpplysningerRepository.findAllByForelagtIsNotNull() // todo bør bare gjelde for siste x mnd

        fun finnOrgNrForMeldinger(
            melding: ForelagteOpplysningerDbRecord,
            // vedtaksperiodeBehandling: FullVedtaksperiodeBehandling,
        ): List<String>

            // List<RelevantMeldingInfo>
        {
//            @Table(value = "forelagte_opplysninger_ainntekt")
// public final data class ForelagteOpplysningerDbRecord(
//    val id: String? = null,
//    val fnr: String? = null,
//    val vedtaksperiodeId: String,
//    val behandlingId: String,
//    val forelagteOpplysningerMelding: PGobject,
//    val opprettet: Instant,
//    val forelagt: Instant?
// )

            /*

            finn id fra vedtaksperiode_behandling  ved å søke på behandlingsid og vedtaksperiode_id
07:19
denne iden finner du igjen som vedtaksperiode_behandling_id  i tabellen vedtaksperiode_behandling_sykepengesoknad  som da også har sykepengesoknad_uuid
07:20
image.png

image.png


07:21
og da finner man orgnummer i tabellen sykepengesoknad  via SYKEPENGESOKNAD_UUID
07:21
og videre orgnavn organisasjonRepository.findByOrgnummer(soknaden.orgnummer)?.navn



             */

            val relevantBehandlingsid = melding.behandlingId
            val relevantVedtaksperiodeid = melding.vedtaksperiodeId

            val vedtaksperiodeBehandlingId =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId = relevantVedtaksperiodeid,
                    behandlingId = relevantBehandlingsid,
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

//            val returnValue =
//                relevanteSykepengesoknader.mapNotNull {
//                    if (it.sykepengesoknadUuid != null && it.orgnummer != null) {
//                        RelevantMeldingInfo(
//                            vedtaksperiodeBehandlingId = vedtaksperiodeBehandlingId!!,
//                            sykepengesoknadUuid = it.sykepengesoknadUuid,
//                            orgnummer = it.orgnummer,
//                        )
//                    } else {
//                        null
//                    }
//                }


            val relevanteOrgnr = relevanteSykepengesoknader.mapNotNull{it.orgnummer}

            return relevanteOrgnr
        }



        for (usendtMelding in usendteMeldinger) {
            val fnr = usendtMelding.fnr
            //val orgnummerForMelding = finnOrgNrForMeldinger(usendtMelding)
            if (fnr == null) {
                continue
            }

            // todo should filter for last sent when I make this
            val nyligSendteMeldingerTilPerson = sendteMeldinger.filter { it.fnr == fnr }.filter {
                    it.opprettet.isAfter(
                        now.minus(
                            Duration.ofDays(28),
                        ),
                    )
                }

            if (nyligSendteMeldingerTilPerson.isNotEmpty()) {

                val orgnrForUsendtMelding = finnOrgNrForMeldinger(usendtMelding).firstOrNull()
                val orgnummerForSendtMeldinger = sendteMeldinger.flatMap {finnOrgNrForMeldinger(it)}

                if (orgnrForUsendtMelding != null && orgnummerForSendtMeldinger.contains(orgnrForUsendtMelding)) {
                    resultat[CronJobStatus.HAR_FATT_NYLIG_VARSEL] = (resultat[CronJobStatus.HAR_FATT_NYLIG_VARSEL] ?: 0) + 1
                } else {
                    // todo send varsel
                }


                // val altForVedtaksperiode = hentAltForPerson.hentAltForVedtaksperiode(usendtMelding.vedtaksperiodeId)
                //relevantOrgnrOgSykepengesoknadUuid = finnOrgNrForMeldinger(usendtMelding, altForVedtaksperiode)
                // altForVedtaksperiode.first().soknader.first().orgnummer

//                  val usendteMeldingerTilPerson =
//                usendteMeldinger.filter { it.fnr == fnr }

            // val altForPerson = hentAltForPerson.hentAltForPerson(fnr)

            } else {

            }




        }

        // finn varslinger nyere enn 2 mnd, finn ut om de hører til det samme orgnr

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
