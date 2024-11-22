package no.nav.helse.flex.forelagteopplysningerainntekt

import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Instant

class TotaltAntallForelagteOpplysningerSjekkTest {

    @Test
    fun `burde trigge dersom antall er for høyt`() {
        val sjekk = TotaltAntallForelagteOpplysningerSjekk(maxAntallForelagteOpplysninger = 1)
        invoking {
            sjekk.sjekk(
                listOf(
                    lagTestForelagteOpplysninger(),
                    lagTestForelagteOpplysninger(),
                )
            )
        } `should throw` RuntimeException::class
    }

    @Test
    fun `burde ikke trigge dersom antall er lavt nok`() {
        val sjekk = TotaltAntallForelagteOpplysningerSjekk(maxAntallForelagteOpplysninger = 1)
        invoking {
            sjekk.sjekk(
                listOf(
                    lagTestForelagteOpplysninger(),
                )
            )
        } `should not throw` AnyException
    }
}

private fun lagTestForelagteOpplysninger(forelagt: Instant? = null): ForelagteOpplysningerDbRecord {
    return ForelagteOpplysningerDbRecord(
        id = "test-id",
        vedtaksperiodeId = "_",
        behandlingId = "_",
        forelagteOpplysningerMelding =
        PGobject().apply {
            type = "json"
            value = "{}"
        },
        opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
        forelagt = forelagt,
    )
}
