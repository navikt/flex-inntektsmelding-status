package no.nav.helse.flex.melding

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class MeldingKafkaDto(
    val opprettMelding: OpprettMelding? = null,
    val lukkMelding: LukkMelding? = null,
    val fnr: String
)

data class LukkMelding(
    val timestamp: Instant
)

enum class Variant {
    @JsonProperty("info")
    INFO,

    @JsonProperty("warning")
    WARNING,

    @JsonProperty("success")
    SUCCESS,

    @JsonProperty("error")
    ERROR
}

data class OpprettMelding(
    val tekst: String,
    val lenke: String?,
    val variant: Variant,
    val lukkbar: Boolean,
    val meldingType: String,
    val synligFremTil: Instant? = null
)
