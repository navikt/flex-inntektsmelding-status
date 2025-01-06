package no.nav.helse.flex.util

import java.time.DayOfWeek.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDate.erRettFør(other: LocalDate): Boolean =
    this < other &&
        when (this.dayOfWeek) {
            MONDAY, TUESDAY, WEDNESDAY, THURSDAY, SUNDAY -> this.plusDays(1) == other
            FRIDAY -> other in this.plusDays(1)..this.plusDays(3)
            SATURDAY -> other in this.plusDays(1)..this.plusDays(2)
            else -> false
        }

fun LocalDateTime.tilOsloInstant(): Instant = this.atZone(osloZone).toInstant()

fun datoIntervallString(intervall: Pair<LocalDate, LocalDate>): String {
    val (startDate, endDate) = intervall

    val dayFormatter = DateTimeFormatter.ofPattern("dd.", norskLocale)
    val monthFormatter = DateTimeFormatter.ofPattern("dd. MMMM", norskLocale)
    val fullFormatter = DateTimeFormatter.ofPattern("dd. MMMM yyyy", norskLocale)

    return when {
        startDate.year == endDate.year && startDate.month == endDate.month ->
            "${startDate.format(dayFormatter)} - ${endDate.format(monthFormatter)}"
        startDate.year == endDate.year ->
            "${startDate.format(monthFormatter)} - ${endDate.format(monthFormatter)}"
        else ->
            "${startDate.format(fullFormatter)} - ${endDate.format(fullFormatter)}"
    }
}
