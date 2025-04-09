package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.ForsinkelseFraOpprinnelseTilVarselSjekk
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtSammeVedtaksperiodeSjekk
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.toJsonNode
import org.postgresql.util.PGobject
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Component
class SendForelagteOpplysningerOppgave(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val hentRelevantInfoTilForelagtOpplysning: HentRelevantInfoTilForelagtOpplysning,
    private val opprettBrukervarselForForelagteOpplysninger: OpprettBrukervarselForForelagteOpplysninger,
    private val harForelagtSammeVedtaksperiode: HarForelagtSammeVedtaksperiodeSjekk,
    private val forsinkelseFraOpprinnelseTilVarselSjekk: ForsinkelseFraOpprinnelseTilVarselSjekk,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val organisasjonRepository: OrganisasjonRepository,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun sendForelagteOpplysninger(
        forelagteOpplysningerId: String,
        now: Instant,
    ): Boolean {
        val forelagteOpplysninger = forelagteOpplysningerRepository.findById(forelagteOpplysningerId).getOrNull()
        if (forelagteOpplysninger == null) {
            log.error("Forelagte opplysninger finnes ikke for id: $forelagteOpplysningerId")
            return false
        }

        val erSpesiellForelagtOpplsyning = (
            forelagteOpplysninger.vedtaksperiodeId == "815c37b4-9291-4d05-8de5-6050bff0374d"
            && forelagteOpplysninger.behandlingId == "02babfa3-d579-4d44-82a3-8a730e164e56"
        )

        if (!erSpesiellForelagtOpplsyning && !forsinkelseFraOpprinnelseTilVarselSjekk.sjekk(forelagteOpplysninger, now)) {
            return false
        }

        val relevantInfoTilForelagteOpplysninger =
            hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
                vedtaksperiodeId = forelagteOpplysninger.vedtaksperiodeId,
                behandlingId = forelagteOpplysninger.behandlingId,
            ) ?: run {
                if (erSpesiellForelagtOpplsyning) {
                    val soknad: Sykepengesoknad? = sykepengesoknadRepository.findBySykepengesoknadUuid("85a2d1e3-6294-3e3d-b814-bdb838f92f47")
                    requireNotNull(soknad) { "søknad for spesifikk forelagt opplysning finnes ikke" }
                    requireNotNull(soknad.orgnummer) { "spesifikk soknad skal ha orgnummer" }
                    val org = organisasjonRepository.findByOrgnummer(soknad.orgnummer)
                    requireNotNull(org) { "organisasjon for spesifikk soknad finnes ikke" }
                    RelevantInfoTilForelagtOpplysning(
                        fnr = soknad.fnr,
                        startSyketilfelle = soknad.startSyketilfelle,
                        orgnummer = soknad.orgnummer,
                        orgNavn = org.navn,
                    )
                } else null
            }

        if (relevantInfoTilForelagteOpplysninger == null) {
            log.warn("Kunne ikke hente relevant info til forelagte opplysninger: ${forelagteOpplysninger.id}")
            return false
        }

        if (harForelagtSammeVedtaksperiode.sjekk(
                fnr = relevantInfoTilForelagteOpplysninger.fnr,
                vedtaksperiodeId = forelagteOpplysninger.vedtaksperiodeId,
                forelagteOpplysningerId = forelagteOpplysninger.id!!,
            )
        ) {
            log.error(
                "Har forelagt samme vedtaksperiode id tidligere. " +
                    "Forelagte opplysninger: ${forelagteOpplysninger.id}. " +
                    "Skal vi forelegge for denne personen på nytt? Mulig vi får feil data fra spleis.",
            )
            return false
        }

        opprettBrukervarselForForelagteOpplysninger.opprettVarslinger(
            varselId = forelagteOpplysninger.id!!,
            melding = forelagteOpplysninger.forelagteOpplysningerMelding,
            fnr = relevantInfoTilForelagteOpplysninger.fnr,
            orgNavn = relevantInfoTilForelagteOpplysninger.orgNavn,
            opprinneligOpprettet = forelagteOpplysninger.opprinneligOpprettet,
            startSyketilfelle = relevantInfoTilForelagteOpplysninger.startSyketilfelle,
        )
        forelagteOpplysningerRepository.save(
            forelagteOpplysninger.copy(forelagt = now),
        )
        return true
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
            inntekter =
                deserialisertMelding.skatteinntekter.map {
                    AaregInntekt.Inntekt(
                        it.måned.toString(),
                        it.beløp,
                    )
                },
            omregnetAarsinntekt = deserialisertMelding.omregnetÅrsinntekt,
            orgnavn = orgNavn,
        )
    return aaregInntekt.toJsonNode()
}
