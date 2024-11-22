package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtForPersonMedOrgNyligSjekk
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Instant
import java.time.LocalDate
import java.util.*

class SendForelagteOpplysningerOppgaveTest {

    private fun harForelagtForPersonMedOrgNyligSjekkMock(): HarForelagtForPersonMedOrgNyligSjekk {
        return mock<HarForelagtForPersonMedOrgNyligSjekk> {
            on { sjekk(any(), any(), any()) } doReturn true
        }
    }

    private fun hentRelevantInfoTilForelagtOpplysningMock(): HentRelevantInfoTilForelagtOpplysning {
        return mock<HentRelevantInfoTilForelagtOpplysning> {
            on { hentRelevantInfoTil(any()) } doReturn RelevantInfoTilForelagtOpplysning(
                fnr = "test-fnr",
                orgnummer = "test-org",
                startSyketilfelle = LocalDate.parse("2022-06-16"),
                orgNavn = "Test Org",
            )
        }
    }

    private fun opprettBrukervarselForForelagteOpplysningerMock(): OpprettBrukervarselForForelagteOpplysninger {
        return mock()
    }

    @Test
    fun `burde lagre forelagt tidspunkt lagret db etter forelagt`() {
        val forelagtOpplysning = lagTestForelagteOpplysninger(forelagt = null)

        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository = mock {
            on { findById(any()) } doReturn Optional.of(forelagtOpplysning)
        }

        val forelagtTidspunkt = Instant.parse("2024-01-29T00:00:00.00Z")
        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepository,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock(),
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock(),
                harForelagtForPersonMedOrgNyligSjekk = harForelagtForPersonMedOrgNyligSjekkMock()
            )

        oppgave.sendForelagteOpplysninger("_", forelagtTidspunkt)

        verify(forelagteOpplysningerRepository).save(forelagtOpplysning.copy(forelagt = forelagtTidspunkt))
    }

    fun lagTestForelagteOpplysninger(forelagt: Instant? = null): ForelagteOpplysningerDbRecord {
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
}
