package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.any
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.postgresql.util.PGobject
import java.time.Instant
import java.util.*

class SendForelagteOpplysningerOppgaveTest {
    val forelagteOpplysningerRepositoryMock = Mockito.mock(ForelagteOpplysningerRepository::class.java)
    val hentRelevantInfoTilForelagtOpplysningMock = Mockito.mock(HentRelevantInfoTilForelagtOpplysning::class.java)
    val opprettBrukervarselForForelagteOpplysningerMock =
        Mockito.mock(OpprettBrukervarselForForelagteOpplysninger::class.java)

    @AfterEach
    fun resetMocks() {
        Mockito.reset(forelagteOpplysningerRepositoryMock)
        Mockito.reset(hentRelevantInfoTilForelagtOpplysningMock)
        Mockito.reset(opprettBrukervarselForForelagteOpplysningerMock)
    }

    @BeforeEach
    fun mockSetup() {
        Mockito.`when`(hentRelevantInfoTilForelagtOpplysningMock.hentForelagteOpplysningerFor(any(), any())).thenReturn(
            emptyList(),
        )
        Mockito.`when`(hentRelevantInfoTilForelagtOpplysningMock.hentRelevantInfoTil(any())).thenReturn(
            RelevantInfoTilForelagtOpplysning(
                fnr = "identisk-test-fnr",
                orgnummer = "identisk-test-org",
                orgNavn = "Test Org",
            ),
        )
        Mockito.`when`(forelagteOpplysningerRepositoryMock.findById(any())).thenReturn(
            Optional.of(lagTestForelagteOpplysninger(forelagt = null)),
        )
    }

    @Test
    fun `En opplysning burde ikke bli forelagt dersom en tidligere opplysning er forelagt i nylig tid`() {
        val tidligereForelagtTidspunkt = Instant.parse("2024-01-01T00:00:00.00Z")
        val skalIkkeVarsleTidspunkt = Instant.parse("2024-01-28T00:00:00.00Z")

        Mockito.`when`(hentRelevantInfoTilForelagtOpplysningMock.hentForelagteOpplysningerFor(any(), any())).thenReturn(
            listOf(
                lagTestForelagteOpplysninger(forelagt = tidligereForelagtTidspunkt)
            ),
        )

        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepositoryMock,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock,
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock,
            )
        oppgave.sendForelagteOpplysninger("_", skalIkkeVarsleTidspunkt)

        Mockito.verify(opprettBrukervarselForForelagteOpplysningerMock, Mockito.times(0))
            .opprettVarslinger(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `En opplysning burde bli forelagt dersom en opplysning er forelagt for en stund siden`() {
        val tidligereForelagtTidspunkt = Instant.parse("2024-01-01T00:00:00.00Z")
        val skalVarsleTidspunkt = Instant.parse("2024-01-29T00:00:00.00Z")

        Mockito.`when`(hentRelevantInfoTilForelagtOpplysningMock.hentForelagteOpplysningerFor(any(), any())).thenReturn(
            listOf(
                lagTestForelagteOpplysninger(forelagt = tidligereForelagtTidspunkt)
            )
        )

        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepositoryMock,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock,
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock,
            )
        oppgave.sendForelagteOpplysninger("_", skalVarsleTidspunkt)

        Mockito.verify(opprettBrukervarselForForelagteOpplysningerMock)
            .opprettVarslinger(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `burde ha forelagt tidspunkt lagret i db`() {
        val forelagtOpplysning = lagTestForelagteOpplysninger(forelagt = null)
        Mockito.`when`(forelagteOpplysningerRepositoryMock.findById(any())).thenReturn(
            Optional.of(forelagtOpplysning),
        )

        val forelagtTidspunkt = Instant.parse("2024-01-29T00:00:00.00Z")
        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepositoryMock,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock,
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock,
            )
        oppgave.sendForelagteOpplysninger("_", forelagtTidspunkt)
        Mockito.verify(forelagteOpplysningerRepositoryMock).save(forelagtOpplysning.copy(forelagt = forelagtTidspunkt))
    }

    fun lagTestForelagteOpplysninger(
        forelagt: Instant? = null
    ): ForelagteOpplysningerDbRecord {
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
