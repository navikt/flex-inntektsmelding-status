package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.brukervarsel.Brukervarsel
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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

@Component
class SendForelagteOpplysningerCronjob(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val sendForelagteOpplysningerOppgave: SendForelagteOpplysningerOppgave,
) {
    private val log = logger()

    @Scheduled(initialDelay = 10, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
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
            sendForelagteOpplysningerOppgave.sendForelagteOpplysninger(usendtForelagtOpplysning.id!!, now)
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

@Component
class SendForelagteOpplysningerOppgave(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val organisasjonRepository: OrganisasjonRepository,
    private val brukervarsel: Brukervarsel,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    @Value("\${FORELAGTE_OPPLYSNINGER_BASE_URL}") private val forelagteOpplysningerBaseUrl: String,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun sendForelagteOpplysninger(forelagteOpplysningerId: String, now: Instant) {
        val forelagteOpplysninger = forelagteOpplysningerRepository.findById(forelagteOpplysningerId).getOrNull()
        if (forelagteOpplysninger == null) {
            log.error("Forelagte opplysninger finnes ikke for id: $forelagteOpplysningerId")
            return
        }

        val relevanteSykepengesoknader =
            finnSykepengesoknader(
                vedtaksperiodeId = forelagteOpplysninger.vedtaksperiodeId,
                behandlingId = forelagteOpplysninger.behandlingId,
            )
        if (relevanteSykepengesoknader.isEmpty()) {
            log.error("Fant ingen sykepengesøknader relatert til forelagte opplysninger: ${forelagteOpplysninger.id}")
            return
        }

        // TODO: Sjekk at det er riktig/holder å bruke siste fnr
        val sykepengesoknadFnr = relevanteSykepengesoknader.maxByOrNull { it.tom }!!.fnr

        val fnr = forelagteOpplysninger.fnr ?: sykepengesoknadFnr

        // TODO: Holder det å logge dersom det er flere søknader, og bare bruke siste?
        relevanteSykepengesoknader.forEach {
            if (it.orgnummer == null) {
                log.warn("Orgnummer er tom")
                return@forEach
            }
            if (!harForelagtNyligForOrgnr(it.fnr, it.orgnummer, now)) {
                sendForelagteMelding(
                    fnr = fnr,
                    orgnummer = it.orgnummer,
                    melding = forelagteOpplysninger,
                    now = now,
                )
                forelagteOpplysningerRepository.save(
                    forelagteOpplysninger.copy(forelagt = now)
                )
            }
        }
    }

    private fun harForelagtNyligForOrgnr(
        fnr: String,
        orgnr: String,
        now: Instant,
    ): Boolean {
        val forelagtEtter = now.minus(Duration.ofDays(28))
        val forelagteOpplysninger = finnForelagteOpplysningerForPersonOgOrg(fnr, orgnr)
        val finnesOpplysningerForelagtNylig = forelagteOpplysninger
            .mapNotNull { it.forelagt }
            .any { it.isAfter(forelagtEtter) }
        return finnesOpplysningerForelagtNylig
    }

    fun sendForelagteMelding(
        fnr: String,
        orgnummer: String?,
        melding: ForelagteOpplysningerDbRecord,
        now: Instant,
        dryRun: Boolean = false,
    ): CronJobStatus {
        val orgnavn =
            if (orgnummer == null) {
                "arbeidsgiver"
            } else {
                organisasjonRepository.findByOrgnummer(orgnummer)?.navn ?: orgnummer
            }

        if (!dryRun) {
            val forelagtOpplysningId = melding.id!!
            val synligFremTil = OffsetDateTime.now().plusWeeks(3).toInstant()
            val lenkeTilForelagteOpplysninger = "$forelagteOpplysningerBaseUrl/$forelagtOpplysningId"

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

            log.info("Sendt forelagte opplysninger varsel for vedtaksperiode ${melding.vedtaksperiodeId}")
        }

        return CronJobStatus.SENDT_FORELAGTE_OPPLYSNINGER
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

    private fun finnForelagteOpplysningerForPersonOgOrg(fnr: String, orgnr: String): List<ForelagteOpplysningerDbRecord> {
        val sykepengesoknader = sykepengesoknadRepository.findByFnr(fnr)
            .filter { it.orgnummer == orgnr }
        val sykepengesoknadUuids = sykepengesoknader.map { it.sykepengesoknadUuid }
        val relasjoner =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(sykepengesoknadUuids)
        val vedtaksperiodeBehandlinger = relasjoner.map {
            vedtaksperiodeBehandlingRepository.findById(it.vedtaksperiodeBehandlingId).get()
        }
        return vedtaksperiodeBehandlinger.flatMap {
            forelagteOpplysningerRepository.findAllByVedtaksperiodeIdAndBehandlingId(it.vedtaksperiodeId, it.behandlingId)
        }
    }
}

enum class CronJobStatus { SENDT_FORELAGTE_OPPLYSNINGER }
