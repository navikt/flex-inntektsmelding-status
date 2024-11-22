package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.TotaltAntallForelagteOpplysningerSjekk
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Instant

private val ANY_INSTANT = Instant.parse("2000-01-01T00:00:00.00Z")

class SendForelagteOpplysningerCronJobTest {
    @Test
    fun `burde kalle på totaltAntallForelagteOpplysningerSjekk med alle forelagte opplysninger`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findAllByForelagtIsNull() } doReturn
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

private fun lagTestForelagteOpplysninger(
    id: String = "test-id",
    forelagt: Instant? = null,
): ForelagteOpplysningerDbRecord {
    return ForelagteOpplysningerDbRecord(
        id = id,
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
