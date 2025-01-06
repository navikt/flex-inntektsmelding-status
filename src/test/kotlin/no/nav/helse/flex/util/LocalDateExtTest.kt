package no.nav.helse.flex.util

import org.amshove.kluent.`should be equal to`
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

    @Test
    fun `datoIntervallString burde returnere riktig formatert tekst`() {
        datoIntervallString(
            LocalDate.parse("2023-09-13") to LocalDate.parse("2023-09-14"),
        ) `should be equal to` "13. - 14. september"

        datoIntervallString(
            LocalDate.parse("2023-08-12") to LocalDate.parse("2023-09-05"),
        ) `should be equal to` "12. august - 05. september"

        datoIntervallString(
            LocalDate.parse("2023-12-13") to LocalDate.parse("2024-01-03"),
        ) `should be equal to` "13. desember 2023 - 03. januar 2024"
    }
}
