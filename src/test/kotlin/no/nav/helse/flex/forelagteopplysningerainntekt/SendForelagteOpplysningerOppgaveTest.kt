package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.ForsinkelseFraOpprinnelseTilVarselSjekk
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtSammeVedtaksperiodeSjekk
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
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

    private fun vedtaksperiodeBehandlingRepositoryMock(): VedtaksperiodeBehandlingRepository = mock()

    @Test
    fun `burde lagre forelagt tidspunkt i db etter forelagt varsel er sendt`() {
        val forelagtOpplysning = lagTestForelagteOpplysninger(statusEndret = null)

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
                vedtaksperiodeBehandlingRepository = vedtaksperiodeBehandlingRepositoryMock(),
            )

        oppgave.sendForelagteOpplysninger("_", forelagtTidspunkt)

        verify(forelagteOpplysningerRepository).save(forelagtOpplysning.copy(statusEndret = forelagtTidspunkt))
    }

    @Test
    fun `burde returnere true dersom sjekker er gyldige`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(statusEndret = null))
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
                vedtaksperiodeBehandlingRepository = vedtaksperiodeBehandlingRepositoryMock(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeTrue()
    }

    @Test
    fun `burde returnere false dersom sjekk for nylig forelagt feiler`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(statusEndret = null))
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
                vedtaksperiodeBehandlingRepository = vedtaksperiodeBehandlingRepositoryMock(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeFalse()
    }

    @Test
    fun `burde returnere false dersom sjekk for forsinkelse fra opprinnelse feiler`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(statusEndret = null))
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
                vedtaksperiodeBehandlingRepository = vedtaksperiodeBehandlingRepositoryMock(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeFalse()
    }

    @Test
    fun `burde håndtere forelagt oppplysning som er sendt til behandling utenfor speil`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(statusEndret = null))
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
                vedtaksperiodeBehandlingRepository = vedtaksperiodeBehandlingRepositoryMock(),
            )
        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeFalse()
        verify(
            forelagteOpplysningerRepository,
        ).save(
            lagTestForelagteOpplysninger(
                statusEndret = Instant.parse("2024-01-01T00:00:00.00Z"),
            ).copy(status = ForelagtStatus.IKKE_FORELAGT),
        )
    }

    fun lagTestForelagteOpplysninger(statusEndret: Instant? = null): ForelagteOpplysningerDbRecord =
        ForelagteOpplysningerDbRecord(
            id = "test-id",
            vedtaksperiodeId = "_",
            behandlingId = "_",
            forelagteOpplysningerMelding =
                PGobject().apply {
                    type = "json"
                    value = "{}"
                },
            opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            statusEndret = statusEndret,
            status = ForelagtStatus.SKAL_FORELEGGES,
            opprinneligOpprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
        )
}
