package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.Instant

data class ForelagteOpplysningerResponse(
    val id: String?,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val forelagteOpplysningerMelding: ForelagteOpplysningerMelding?,
    val opprettet: Instant,
    val opprinneligOpprettet: Instant,
    val status: ForelagtStatus,
    val statusEndret: Instant?,
)

fun ForelagteOpplysningerDbRecord.toResponse(): ForelagteOpplysningerResponse {
    val meldingJson: String? = forelagteOpplysningerMelding.value
    val melding =
        if (meldingJson == null) {
            null
        } else {
            objectMapper.readValue<ForelagteOpplysningerMelding>(meldingJson)
        }

    return ForelagteOpplysningerResponse(
        id = id,
        vedtaksperiodeId = vedtaksperiodeId,
        behandlingId = behandlingId,
        forelagteOpplysningerMelding = melding,
        opprettet = opprettet,
        opprinneligOpprettet = opprinneligOpprettet,
        status = status,
        statusEndret = statusEndret,
    )
}
