package no.nav.helse.flex.util

import java.time.DayOfWeek.*
import java.time.LocalDate

fun LocalDate.erRettFør(other: LocalDate): Boolean =
    this < other && when (this.dayOfWeek) {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, SUNDAY -> this.plusDays(1) == other
        FRIDAY -> other in this.plusDays(1)..this.plusDays(3)
        SATURDAY -> other in this.plusDays(1)..this.plusDays(2)
        else -> false
    }
