package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.config.unleash.UnleashToggles
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.TotaltAntallForelagteOpplysningerSjekk
import org.junit.jupiter.api.Test
import java.time.Instant

private val ANY_INSTANT = Instant.parse("2000-01-01T00:00:00.00Z")

class SendForelagteOpplysningerCronJobTest {
    @Test
    fun `burde kalle på totaltAntallForelagteOpplysningerSjekk med alle forelagte opplysninger`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findAllByStatus(ForelagtStatus.SKAL_FORELEGGES) } doReturn
                    listOf(
                        lagTestForelagteOpplysninger(id = "1"),
                        lagTestForelagteOpplysninger(id = "2"),
                    )
            }
        val totaltAntallForelagteOpplysningerSjekk: TotaltAntallForelagteOpplysningerSjekk = mock()

        val sendForelagteOpplysningerCronjob =
            SendForelagteOpplysningerCronjob(
                forelagteOpplysningerRepository = forelagteOpplysningerRepository,
                sendForelagteOpplysningerOppgave = mock<SendForelagteOpplysningerOppgave>(),
                totaltAntallForelagteOpplysningerSjekk = totaltAntallForelagteOpplysningerSjekk,
                unleashToggles = mock<UnleashToggles>(),
            )

        sendForelagteOpplysningerCronjob.runMedParameter(ANY_INSTANT)

        verify(totaltAntallForelagteOpplysningerSjekk).sjekk(
            listOf(
                lagTestForelagteOpplysninger(id = "1"),
                lagTestForelagteOpplysninger(id = "2"),
            ),
        )
    }
}
