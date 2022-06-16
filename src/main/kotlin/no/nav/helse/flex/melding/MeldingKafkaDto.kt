package no.nav.helse.flex.melding

import java.time.Instant

data class MeldingKafkaDto(
    val opprettMelding: OpprettMelding?,
    val lukkMelding: LukkMelding?,
    val fnr: String,
)
data class LukkMelding(
    val timestamp: Instant,
)

enum class Variant {
    info,
    warning,
    success,
    error,
}

data class OpprettMelding(
    val tekst: String,
    val lenke: String,
    val variant: Variant,
    val lukkbar: Boolean,
    val meldingType: String,
    val synligFremTil: Instant?,
)
