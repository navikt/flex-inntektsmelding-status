package no.nav.helse.flex.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

val osloZone = ZoneId.of("Europe/Oslo")
val norskLocale =
    Locale.Builder()
        .setLanguage("nb")
        .setRegion("NO")
        .build()
val norskDateFormat = DateTimeFormatter.ofPattern("d. MMMM yyyy").localizedBy(norskLocale)

fun OffsetDateTime.tilOsloZone(): OffsetDateTime = this.atZoneSameInstant(osloZone).toOffsetDateTime()

fun Instant.tilOsloZone(): OffsetDateTime = this.atZone(osloZone).toOffsetDateTime()

fun Instant.tilLocalDate(): LocalDate = this.tilOsloLocalDateTime().toLocalDate()

fun Instant.tilOsloLocalDateTime(): LocalDateTime = this.tilOsloZone().toLocalDateTime()
