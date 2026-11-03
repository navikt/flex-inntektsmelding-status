package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import no.nav.helse.flex.forelagteopplysningerainntekt.lagTestForelagteOpplysninger
import org.junit.jupiter.api.Test

class TotaltAntallForelagteOpplysningerSjekkTest {
    @Test
    fun `burde trigge dersom antall er for høyt`() {
        val sjekk = TotaltAntallForelagteOpplysningerSjekk(maxAntallForelagteOpplysninger = 1)
        shouldThrow<RuntimeException> {
            sjekk.sjekk(
                listOf(
                    lagTestForelagteOpplysninger(),
                    lagTestForelagteOpplysninger(),
                ),
            )
        }
    }

    @Test
    fun `burde ikke trigge dersom antall er lavt nok`() {
        val sjekk = TotaltAntallForelagteOpplysningerSjekk(maxAntallForelagteOpplysninger = 1)
        shouldNotThrowAny {
            sjekk.sjekk(
                listOf(
                    lagTestForelagteOpplysninger(),
                ),
            )
        }
    }
}
