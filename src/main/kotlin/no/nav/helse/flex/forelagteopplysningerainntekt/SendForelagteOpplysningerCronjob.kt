package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.toJsonNode
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

@Profile("forelagteopplysninger")
@Component
class SendForelagteOpplysningerCronjob(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val sendForelagteOpplysningerOppgave: SendForelagteOpplysningerOppgave,
    private val totaltAntallForelagteOpplysningerSjekk: TotaltAntallForelagteOpplysningerSjekk,
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

        totaltAntallForelagteOpplysningerSjekk.sjekk(usendteForelagteOpplysninger)

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
    val startSyketilfelle: LocalDate,
    val orgnummer: String,
    val orgNavn: String,
)

@Component
class HentRelevantInfoTilForelagtOpplysning(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val organisasjonRepository: OrganisasjonRepository,
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
            startSyketilfelle = sisteSykepengeSoknad.startSyketilfelle,
            orgNavn = org.navn,
        )
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
    private val opprettBrukervarselForForelagteOpplysninger: OpprettBrukervarselForForelagteOpplysninger,
    private val harForelagtForPersonMedOrgNyligSjekk: HarForelagtForPersonMedOrgNyligSjekk,
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

        if (!harForelagtForPersonMedOrgNyligSjekk.sjekk(
                fnr = relevantInfoTilForelagteOpplysninger.fnr,
                orgnummer = relevantInfoTilForelagteOpplysninger.orgnummer,
                now = now,
            )
        ) {
            log.warn(
                "Har forelagt nylig for person med org, forelgger ikke på nytt nå. Forelagte opplysninger: ${forelagteOpplysninger.id}",
            )
            return
        }

        opprettBrukervarselForForelagteOpplysninger.opprettVarslinger(
            varselId = forelagteOpplysninger.id!!,
            melding = forelagteOpplysninger.forelagteOpplysningerMelding,
            fnr = relevantInfoTilForelagteOpplysninger.fnr,
            orgNavn = relevantInfoTilForelagteOpplysninger.orgnummer,
            now = now,
            startSyketilfelle = relevantInfoTilForelagteOpplysninger.startSyketilfelle,
            // TODO: dry run
            dryRun = false,
        )
        forelagteOpplysningerRepository.save(
            forelagteOpplysninger.copy(forelagt = now),
        )
    }
}

internal fun forelagtOpplysningTilMetadata(
    forelagtOpplysningMelding: PGobject,
    orgNavn: String,
): JsonNode {
    val serialisertMelding = forelagtOpplysningMelding.value ?: error("Melding må ha value")
    val deserialisertMelding: ForelagteOpplysningerMelding =
        objectMapper.readValue(serialisertMelding)
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
