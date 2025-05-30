package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.ForsinkelseFraOpprinnelseTilVarselSjekk
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtSammeVedtaksperiodeSjekk
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

class SendForelagteOpplysningerOppgaveTest {
    private fun harForelagtForPersonMedOrgNyligSjekkMock(): HarForelagtSammeVedtaksperiodeSjekk =
        mock<HarForelagtSammeVedtaksperiodeSjekk> {
            on { sjekk(any(), any(), any()) } doReturn false
        }

    private fun forsinkelseFraOpprinnelseTilVarselSjekkMock(): ForsinkelseFraOpprinnelseTilVarselSjekk =
        mock<ForsinkelseFraOpprinnelseTilVarselSjekk> {
            on { sjekk(any(), any()) } doReturn true
        }

    private fun hentRelevantInfoTilForelagtOpplysningMock(): HentRelevantInfoTilForelagtOpplysning =
        mock<HentRelevantInfoTilForelagtOpplysning> {
            on { hentRelevantInfoFor(any(), any()) } doReturn
                RelevantInfoTilForelagtOpplysning(
                    fnr = "test-fnr",
                    orgnummer = "test-org",
                    startSyketilfelle = LocalDate.parse("2022-06-16"),
                    orgNavn = "Test Org",
                )
        }

    private fun opprettBrukervarselForForelagteOpplysningerMock(): OpprettBrukervarselForForelagteOpplysninger = mock()

    @Test
    fun `burde lagre status SENDT i db etter forelagt varsel er sendt`() {
        val forelagtOpplysning = lagTestForelagteOpplysninger()

        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(forelagtOpplysning)
            }

        val forelagtTidspunkt = Instant.parse("2024-01-01T00:00:00.00Z")
        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepository,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock(),
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock(),
                harForelagtSammeVedtaksperiode = harForelagtForPersonMedOrgNyligSjekkMock(),
                forsinkelseFraOpprinnelseTilVarselSjekk = forsinkelseFraOpprinnelseTilVarselSjekkMock(),
            )

        oppgave.sendForelagteOpplysninger("_", forelagtTidspunkt)

        verify(
            forelagteOpplysningerRepository,
        ).save(forelagtOpplysning.copy(statusEndret = forelagtTidspunkt, status = ForelagtStatus.SENDT))
    }

    @Test
    fun `burde returnere true dersom valideringssjekker er gyldige`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger())
            }
        val harForelagtSammeVedtaksperiodeSjekk =
            mock<HarForelagtSammeVedtaksperiodeSjekk> {
                on { sjekk(any(), any(), any()) } doReturn false
            }
        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepository,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock(),
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock(),
                harForelagtSammeVedtaksperiode = harForelagtSammeVedtaksperiodeSjekk,
                forsinkelseFraOpprinnelseTilVarselSjekk = forsinkelseFraOpprinnelseTilVarselSjekkMock(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.`should be true`()
    }

    @Test
    fun `burde kalle HarForelagtSammeVedtaksperiodeSjekk`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger())
            }
        val harForelagtSammeVedtaksperiodeSjekk: HarForelagtSammeVedtaksperiodeSjekk =
            mock {
                on { sjekk(any(), any(), any()) } doReturn true
            }
        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepository,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock(),
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock(),
                harForelagtSammeVedtaksperiode = harForelagtSammeVedtaksperiodeSjekk,
                forsinkelseFraOpprinnelseTilVarselSjekk = forsinkelseFraOpprinnelseTilVarselSjekkMock(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.`should be false`()
    }

    @Test
    fun `burde returnere false dersom sjekk for forsinkelse fra opprinnelse feiler`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger())
            }
        val forsinkelseFraOpprinnelseTilVarselSjekk: ForsinkelseFraOpprinnelseTilVarselSjekk =
            mock {
                on { sjekk(any(), any()) } doReturn false
            }
        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepository,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock(),
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock(),
                harForelagtSammeVedtaksperiode = harForelagtForPersonMedOrgNyligSjekkMock(),
                forsinkelseFraOpprinnelseTilVarselSjekk = forsinkelseFraOpprinnelseTilVarselSjekk,
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.`should be false`()
    }

    @Test
    fun `burde sette status til AVBRUTT for forelagt oppplysning som mangler relevant info`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger())
            }
        val hentRelevantInfoTilForelagtOpplysningMock: HentRelevantInfoTilForelagtOpplysning =
            mock<HentRelevantInfoTilForelagtOpplysning> {
                on { hentRelevantInfoFor(any(), any()) } doReturn null
            }
        val oppgave =
            SendForelagteOpplysningerOppgave(
                forelagteOpplysningerRepository = forelagteOpplysningerRepository,
                hentRelevantInfoTilForelagtOpplysning = hentRelevantInfoTilForelagtOpplysningMock,
                opprettBrukervarselForForelagteOpplysninger = opprettBrukervarselForForelagteOpplysningerMock(),
                harForelagtSammeVedtaksperiode = harForelagtForPersonMedOrgNyligSjekkMock(),
                forsinkelseFraOpprinnelseTilVarselSjekk = forsinkelseFraOpprinnelseTilVarselSjekkMock(),
            )
        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.`should be false`()
        verify(
            forelagteOpplysningerRepository,
        ).save(
            lagTestForelagteOpplysninger(
                statusEndret = Instant.parse("2024-01-01T00:00:00.00Z"),
            ).copy(status = ForelagtStatus.AVBRUTT),
        )
    }
}
