package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.toJsonNode
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.varseltekst.skapForelagteOpplysningerTekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.postgresql.util.PGobject
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

        // TODO:
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

data class RelevantInfoTilForelagtOpplysning(
    val fnr: String,
    val orgnummer: String,
    val orgNavn: String,
)

@Component
class HentRelevantInfoTilForelagtOpplysning(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val organisasjonRepository: OrganisasjonRepository,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
) {
    val log = logger()

    fun hentRelevantInfoTil(forelagteOpplysninger: ForelagteOpplysningerDbRecord): RelevantInfoTilForelagtOpplysning? {
        val sykepengeSoknader =
            finnSykepengesoknader(
                vedtaksperiodeId = forelagteOpplysninger.vedtaksperiodeId,
                behandlingId = forelagteOpplysninger.behandlingId,
            )
        if (sykepengeSoknader.isEmpty()) {
            log.warn("Finnes ingen sykepengesoknader relatert til forelagte opplysninger: ${forelagteOpplysninger.id}")
            return null
        }
        if (sykepengeSoknader.size > 1) {
            log.warn(
                "Fant flere sykepengesoknader for forelagte opplysninger: ${forelagteOpplysninger.id}. " +
                    "Vi baserer fnr og orgnr på den siste",
            )
        }
        val sisteSykepengeSoknad = sykepengeSoknader.maxBy { it.tom }

        if (sisteSykepengeSoknad.orgnummer == null) {
            log.warn("Siste sykepengesøknad for forelagte opplysninger inneholder ikke orgnummer: ${forelagteOpplysninger.id}")
            return null
        }

        val org = organisasjonRepository.findByOrgnummer(sisteSykepengeSoknad.orgnummer)
        if (org == null) {
            log.warn(
                "Organisasjon for forelagte opplysninger finnes ikke. Forelagte opplysninger: " +
                    "${forelagteOpplysninger.id}, orgnummer: ${sisteSykepengeSoknad.orgnummer}",
            )
            return null
        }

        return RelevantInfoTilForelagtOpplysning(
            fnr = sisteSykepengeSoknad.fnr,
            orgnummer = sisteSykepengeSoknad.orgnummer,
            orgNavn = org.navn,
        )
    }

    fun hentForelagteOpplysningerFor(
        fnr: String,
        orgnr: String,
    ): List<ForelagteOpplysningerDbRecord> {
        val sykepengesoknader =
            sykepengesoknadRepository.findByFnr(fnr)
                .filter { it.orgnummer == orgnr }
        val sykepengesoknadUuids = sykepengesoknader.map { it.sykepengesoknadUuid }
        val relasjoner =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(sykepengesoknadUuids)
        val vedtaksperiodeBehandlinger =
            relasjoner.map {
                vedtaksperiodeBehandlingRepository.findById(it.vedtaksperiodeBehandlingId).get()
            }
        return vedtaksperiodeBehandlinger.flatMap {
            forelagteOpplysningerRepository.findAllByVedtaksperiodeIdAndBehandlingId(
                it.vedtaksperiodeId,
                it.behandlingId,
            )
        }
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

@Component
class SendForelagteOpplysningerOppgave(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val hentRelevantInfoTilForelagtOpplysning: HentRelevantInfoTilForelagtOpplysning,
    private val brukervarsel: Brukervarsel,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    @Value("\${FORELAGTE_OPPLYSNINGER_BASE_URL}") private val forelagteOpplysningerBaseUrl: String,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun sendForelagteOpplysninger(
        forelagteOpplysningerId: String,
        now: Instant,
    ) {
        val forelagteOpplysninger = forelagteOpplysningerRepository.findById(forelagteOpplysningerId).getOrNull()
        if (forelagteOpplysninger == null) {
            log.error("Forelagte opplysninger finnes ikke for id: $forelagteOpplysningerId")
            return
        }

        val relevantInfoTilForelagteOpplysninger =
            hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoTil(forelagteOpplysninger)
        if (relevantInfoTilForelagteOpplysninger == null) {
            log.warn("Kunne ikke hente relevant info til forelagte opplysninger: ${forelagteOpplysninger.id}")
            return
        }

        if (!harForelagtNyligForOrgnr(
                fnr = relevantInfoTilForelagteOpplysninger.fnr,
                orgnr = relevantInfoTilForelagteOpplysninger.orgnummer,
                now,
            )
        ) {
            opprettVarslinger(
                relevantInfoTilForelagtOpplysning = relevantInfoTilForelagteOpplysninger,
                melding = forelagteOpplysninger,
                now = now,
            )
            forelagteOpplysningerRepository.save(
                forelagteOpplysninger.copy(forelagt = now),
            )
        }
    }

    private fun harForelagtNyligForOrgnr(
        fnr: String,
        orgnr: String,
        now: Instant,
    ): Boolean {
        val forelagtEtter = now.minus(Duration.ofDays(28))
        val forelagteOpplysninger =
            hentRelevantInfoTilForelagtOpplysning.hentForelagteOpplysningerFor(fnr = fnr, orgnr = orgnr)
        val finnesOpplysningerForelagtNylig =
            forelagteOpplysninger
                .mapNotNull { it.forelagt }
                .any { it.isAfter(forelagtEtter) }
        return finnesOpplysningerForelagtNylig
    }

    fun opprettVarslinger(
        relevantInfoTilForelagtOpplysning: RelevantInfoTilForelagtOpplysning,
        melding: ForelagteOpplysningerDbRecord,
        now: Instant,
        dryRun: Boolean = false,
    ): CronJobStatus {
        if (!dryRun) {
            val forelagtOpplysningId = melding.id!!
            val synligFremTil = now.tilOsloZone().plusWeeks(3).toInstant()
            val lenkeTilForelagteOpplysninger = "$forelagteOpplysningerBaseUrl/$forelagtOpplysningId"

            brukervarsel.beskjedForelagteOpplysninger(
                fnr = relevantInfoTilForelagtOpplysning.fnr,
                bestillingId = forelagtOpplysningId,
                synligFremTil = synligFremTil,
                lenke = lenkeTilForelagteOpplysninger,
            )

            meldingKafkaProducer.produserMelding(
                meldingUuid = forelagtOpplysningId,
                meldingKafkaDto =
                    MeldingKafkaDto(
                        fnr = relevantInfoTilForelagtOpplysning.fnr,
                        opprettMelding =
                            OpprettMelding(
                                tekst = skapForelagteOpplysningerTekst(),
                                lenke = lenkeTilForelagteOpplysninger,
                                variant = Variant.INFO,
                                lukkbar = false,
                                synligFremTil = synligFremTil,
                                meldingType = "FORELAGTE_OPPLYSNINGER",
                                metadata =
                                    forelagtOpplysningTilMetadata(
                                        melding.forelagteOpplysningerMelding,
                                        relevantInfoTilForelagtOpplysning.orgNavn,
                                    ),
                            ),
                    ),
            )

            log.info("Sendt forelagte opplysninger varsel for vedtaksperiode ${melding.vedtaksperiodeId}")
        }

        return CronJobStatus.SENDT_FORELAGTE_OPPLYSNINGER
    }
}

internal fun forelagtOpplysningTilMetadata(
    forelagtOpplysningMelding: PGobject,
    orgNavn: String,
): JsonNode {
    val deserialisertMelding: ForelagteOpplysningerMelding =
        objectMapper.readValue(forelagtOpplysningMelding.serialisertTilString())
    val aaregInntekt =
        AaregInntekt(
            tidsstempel = deserialisertMelding.tidsstempel,
            inntekter = deserialisertMelding.skatteinntekter.map { AaregInntekt.Inntekt(it.måned.toString(), it.beløp) },
            omregnetAarsinntekt = deserialisertMelding.omregnetÅrsinntekt,
            orgnavn = orgNavn,
        )
    return aaregInntekt.toJsonNode()
}

enum class CronJobStatus { SENDT_FORELAGTE_OPPLYSNINGER }
