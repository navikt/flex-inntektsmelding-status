package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.forelagteopplysningerainntekt.lagTestForelagteOpplysninger
import org.amshove.kluent.AnyException
import org.amshove.kluent.invoking
import org.amshove.kluent.`should not throw`
import org.amshove.kluent.`should throw`
import org.junit.jupiter.api.Test

class TotaltAntallForelagteOpplysningerSjekkTest {
    @Test
    fun `burde trigge dersom antall er for høyt`() {
        val sjekk = TotaltAntallForelagteOpplysningerSjekk(maxAntallForelagteOpplysninger = 1)
        invoking {
            sjekk.sjekk(
                listOf(
                    lagTestForelagteOpplysninger(),
                    lagTestForelagteOpplysninger(),
                ),
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
                ),
            )
        } `should not throw` AnyException
    }
}
