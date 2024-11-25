package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtForPersonMedOrgNyligSjekk
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
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
    private val harForelagtForPersonMedOrgNyligSjekk: HarForelagtForPersonMedOrgNyligSjekk,
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

        val relevantInfoTilForelagteOpplysninger =
            hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
                vedtaksperiodeId = forelagteOpplysninger.vedtaksperiodeId,
                behandlingId = forelagteOpplysninger.behandlingId,
            )
        if (relevantInfoTilForelagteOpplysninger == null) {
            log.warn("Kunne ikke hente relevant info til forelagte opplysninger: ${forelagteOpplysninger.id}")
            return false
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
            return false
        }

        opprettBrukervarselForForelagteOpplysninger.opprettVarslinger(
            varselId = forelagteOpplysninger.id!!,
            melding = forelagteOpplysninger.forelagteOpplysningerMelding,
            fnr = relevantInfoTilForelagteOpplysninger.fnr,
            orgNavn = relevantInfoTilForelagteOpplysninger.orgnummer,
            now = now,
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
            inntekter = deserialisertMelding.skatteinntekter.map { AaregInntekt.Inntekt(it.måned.toString(), it.beløp) },
            omregnetAarsinntekt = deserialisertMelding.omregnetÅrsinntekt,
            orgnavn = orgNavn,
        )
    return aaregInntekt.toJsonNode()
}

