package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.ForsinkelseFraOpprinnelseTilVarselSjekk
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtSammeVedtaksperiodeSjekk
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.toJsonNode
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
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
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
) {
    private val log = logger()
    val ikkeForelagtDato: Instant = Instant.EPOCH

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

        if (!forsinkelseFraOpprinnelseTilVarselSjekk.sjekk(forelagteOpplysninger, now)) {
            return false
        }

        val relevantInfoTilForelagteOpplysninger =
            hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
                vedtaksperiodeId = forelagteOpplysninger.vedtaksperiodeId,
                behandlingId = forelagteOpplysninger.behandlingId,
            )
        if (relevantInfoTilForelagteOpplysninger == null) {
            log.warn("Kunne ikke hente relevant info til forelagte opplysninger: ${forelagteOpplysninger.id}")
            forelagteOpplysningerRepository.save(
                forelagteOpplysninger.copy(
                    statusEndret = now,
                    status = ForelagtStatus.IKKE_FORELAGT,
                ),
            )
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
            forelagteOpplysninger.copy(statusEndret = now),
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
