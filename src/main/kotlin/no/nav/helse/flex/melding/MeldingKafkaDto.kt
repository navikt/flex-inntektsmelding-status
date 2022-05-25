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

data class OpprettMelding(
    val tekst: String,
    val lenke: String,
    val meldingType: String,
    val synligFremTil: Instant?,
)
