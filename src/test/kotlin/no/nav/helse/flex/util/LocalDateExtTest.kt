package no.nav.helse.flex.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LocalDateExtTest {
    @Test
    fun `er rett før`() {
        val mandag = LocalDate.of(2018, 1, 1)
        val tirsdag = mandag.plusDays(1)
        val onsdag = tirsdag.plusDays(1)
        val torsdag = onsdag.plusDays(1)
        val fredag = torsdag.plusDays(1)
        val lørdag = fredag.plusDays(1)
        val søndag = lørdag.plusDays(1)

        assertTrue(LocalDate.of(2017, 12, 31).erRettFør(mandag)) { "søndag er rett før mandag" }
        assertFalse(LocalDate.of(2017, 12, 31).erRettFør(tirsdag)) { "søndag er ikke rett før tirsdag" }
        assertTrue(LocalDate.of(2017, 12, 30).erRettFør(mandag)) { "lørdag er rett før mandag" }
        assertTrue(LocalDate.of(2017, 12, 29).erRettFør(mandag)) { "fredag er rett før mandag" }
        assertFalse(LocalDate.of(2017, 12, 28).erRettFør(mandag)) { "forrige torsdag er ikke rett før mandag" }
        assertFalse(mandag.erRettFør(mandag)) { "mandag er ikke rett før seg selv" }

        assertFalse(søndag.erRettFør(tirsdag)) { "søndag er ikke rett før tirsdag" }
        assertTrue(mandag.erRettFør(tirsdag)) { "tirsdag er rett før mandag" }

        assertFalse(torsdag.erRettFør(lørdag)) { "torsdag er ikke rett før lørdag" }
        assertTrue(fredag.erRettFør(lørdag)) { "fredag er rett før lørdag" }
        assertTrue(fredag.erRettFør(søndag)) { "fredag er rett før søndag" }
        assertTrue(lørdag.erRettFør(søndag)) { "lørdag er rett før søndag" }
    }
}
