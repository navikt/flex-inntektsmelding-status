package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.varseltekst.skapForelagteOpplysningerTekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.*
import java.util.concurrent.TimeUnit

@Component
class SendForelagteOpplysningerCronjob(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val hentAltForPerson: HentAltForPerson,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val lockRepository: LockRepository,
    private val organisasjonRepository: OrganisasjonRepository,
    private val brukervarsel: Brukervarsel,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    @Value("\${FORELAGTE_OPPLYSNINGER_BASE_URL}") private val forelagteOpplysninger: String,
) {
    private val log = logger()

    data class RelevantMeldingInfo(
        val vedtaksperiodeBehandlingId: String,
        val sykepengesoknadUuid: String,
        val orgnummer: String,
    )

    fun sendForelagteMelding(
        fnr: String,
        orgnummer: String?,
        melding: ForelagteOpplysningerDbRecord,
        now: Instant,
        dryRun: Boolean = false,
    ): CronJobStatus {
        if (!dryRun) {
            lockRepository.settAdvisoryTransactionLock(fnr)
        }

        val orgnavn =
            if (orgnummer == null) {
                "arbeidsgiver"
            } else {
                organisasjonRepository.findByOrgnummer(orgnummer)?.navn ?: orgnummer
            }

        if (!dryRun) {
            val forelagtOpplysningId = melding.id!!
            val synligFremTil = OffsetDateTime.now().plusWeeks(3).toInstant()
            val lenkeTilForelagteOpplysninger = "$forelagteOpplysninger/$forelagtOpplysningId"

            brukervarsel.beskjedForelagteOpplysninger(
                fnr = fnr,
                bestillingId = forelagtOpplysningId,
                synligFremTil = synligFremTil,
                lenke = lenkeTilForelagteOpplysninger,
            )

            meldingKafkaProducer.produserMelding(
                meldingUuid = forelagtOpplysningId,
                meldingKafkaDto =
                    MeldingKafkaDto(
                        fnr = fnr,
                        opprettMelding =
                            OpprettMelding(
                                tekst = skapForelagteOpplysningerTekst(),
                                lenke = lenkeTilForelagteOpplysninger,
                                variant = Variant.INFO,
                                lukkbar = false,
                                synligFremTil = synligFremTil,
                                meldingType = "FORELAGTE_OPPLYSNINGER",
                                metadata = objectMapper.readTree(melding.forelagteOpplysningerMelding.toString()),
                            ),
                    ),
            )

            forelagteOpplysningerRepository.save(
                melding.copy(
                    forelagt = now,
                ),
            )

            log.info("Sendt forelagte opplysninger varsel for vedtaksperiode ${melding.vedtaksperiodeId}")
        }

        return CronJobStatus.SENDT_FORELAGTE_OPPLYSNINGER
    }

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

        return runMedParameter(osloDatetimeNow.toInstant())
    }

    fun runMedParameter(now: Instant): Map<CronJobStatus, Int> {
        log.info("Starter VarselutsendingCronJob")
        val resultat = HashMap<CronJobStatus, Int>()

        val usendteForelagteOpplysninger: List<ForelagteOpplysningerDbRecord> =
            forelagteOpplysningerRepository.findAllByForelagtIsNull()

        for (usendtForelagtOpplysning in usendteForelagteOpplysninger) {
            val relevanteSykepengesoknader =
                finnSykepengesoknader(
                    vedtaksperiodeId = usendtForelagtOpplysning.vedtaksperiodeId,
                    behandlingId = usendtForelagtOpplysning.behandlingId,
                )
            if (relevanteSykepengesoknader.isEmpty()) {
                log.warn("Fant ingen sykepengesøknader relatert til forelagte opplysninger: ${usendtForelagtOpplysning.id}")
                continue
            }

            val sykepengesoknadFnr = relevanteSykepengesoknader.maxByOrNull { it.tom }!!.fnr

            val fnr = usendtForelagtOpplysning.fnr ?: sykepengesoknadFnr
            relevanteSykepengesoknader.forEach {
                if (it.orgnummer == null) {
                    log.warn("Orgnummer er tom")
                    return@forEach
                }
                if (!harForelagtNyligForOrgnr(fnr, it.orgnummer, now)) {
                    sendForelagteMelding(
                        fnr = fnr,
                        orgnummer = it.orgnummer,
                        melding = usendtForelagtOpplysning,
                        now = now,
                    )
                }
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

    private fun harForelagtNyligForOrgnr(
        fnr: String,
        orgnr: String,
        now: Instant,
    ): Boolean {
        val tidspunkt = now.minus(Duration.ofDays(28))
        val nyligeForeleggelser = forelagteOpplysningerRepository.findByFnrAndForelagtGreaterThan(fnr, tidspunkt)
        val soknaderMedForeleggelser =
            nyligeForeleggelser.map { finnSykepengesoknader(it.vedtaksperiodeId, it.behandlingId) }.flatten()
        val soknaderMedOrgnr = soknaderMedForeleggelser.filter { it.orgnummer == orgnr }

        return soknaderMedOrgnr.isNotEmpty()
    }

    private fun finnSykepengesoknader(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): List<Sykepengesoknad> {
        val vedtaksperiodeBehandlingId =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
            )?.id

        val relevanteVedtaksperiodebehandlingSykepengesoknaderRelations =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingId(
                vedtaksperiodeBehandlingId ?: "",
            )

        val relevanteSykepengesoknader =
            sykepengesoknadRepository.findBySykepengesoknadUuidIn(
                relevanteVedtaksperiodebehandlingSykepengesoknaderRelations.map {
                    it.sykepengesoknadUuid
                },
            )

        return relevanteSykepengesoknader
    }
}

enum class CronJobStatus { SENDT_FORELAGTE_OPPLYSNINGER }
