package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.ForsinkelseFraOpprinnelseTilVarselSjekk
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtSammeVedtaksperiodeSjekk
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Instant
import java.time.LocalDate
import java.util.*

class SendForelagteOpplysningerOppgaveTest {
    private fun harForelagtForPersonMedOrgNyligSjekkMock(): HarForelagtSammeVedtaksperiodeSjekk {
        return mock<HarForelagtSammeVedtaksperiodeSjekk> {
            on { sjekk(any(), any(), any()) } doReturn false
        }
    }

    private fun forsinkelseFraOpprinnelseTilVarselSjekkMock(): ForsinkelseFraOpprinnelseTilVarselSjekk {
        return mock<ForsinkelseFraOpprinnelseTilVarselSjekk> {
            on { sjekk(any(), any()) } doReturn true
        }
    }

    private fun hentRelevantInfoTilForelagtOpplysningMock(): HentRelevantInfoTilForelagtOpplysning {
        return mock<HentRelevantInfoTilForelagtOpplysning> {
            on { hentRelevantInfoFor(any(), any()) } doReturn
                RelevantInfoTilForelagtOpplysning(
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
    fun `burde lagre forelagt tidspunkt i db etter forelagt varsel er sendt`() {
        val forelagtOpplysning = lagTestForelagteOpplysninger(forelagt = null)

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
                sykepengesoknadRepository = mock<SykepengesoknadRepository>(),
                organisasjonRepository = mock<OrganisasjonRepository>(),
            )

        oppgave.sendForelagteOpplysninger("_", forelagtTidspunkt)

        verify(forelagteOpplysningerRepository).save(forelagtOpplysning.copy(forelagt = forelagtTidspunkt))
    }

    @Test
    fun `burde returnere true dersom sjekker er gyldige`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(forelagt = null))
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
                sykepengesoknadRepository = mock<SykepengesoknadRepository>(),
                organisasjonRepository = mock<OrganisasjonRepository>(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeTrue()
    }

    @Test
    fun `burde returnere false dersom sjekk for nylig forelagt feiler`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(forelagt = null))
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
                sykepengesoknadRepository = mock<SykepengesoknadRepository>(),
                organisasjonRepository = mock<OrganisasjonRepository>(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeFalse()
    }

    @Test
    fun `burde returnere false dersom sjekk for forsinkelse fra opprinnelse feiler`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(forelagt = null))
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
                sykepengesoknadRepository = mock<SykepengesoknadRepository>(),
                organisasjonRepository = mock<OrganisasjonRepository>(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeFalse()
    }

    @Test
    fun `sjekk for forsinkelse fra opprinnelse burde ikke feile for spesiell forelagt opplysning`() {
        val forelagteOpplysningerRepository: ForelagteOpplysningerRepository =
            mock {
                on { findById(any()) } doReturn Optional.of(lagTestForelagteOpplysninger(
                    vedtaksperiodeId = "815c37b4-9291-4d05-8de5-6050bff0374d",
                    behandlingId = "02babfa3-d579-4d44-82a3-8a730e164e56",
                    forelagt = null,
                    ))
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
                sykepengesoknadRepository = mock<SykepengesoknadRepository>(),
                organisasjonRepository = mock<OrganisasjonRepository>(),
            )

        val bleSendt = oppgave.sendForelagteOpplysninger("_", Instant.parse("2024-01-01T00:00:00.00Z"))
        bleSendt.shouldBeTrue()
    }

    fun lagTestForelagteOpplysninger(
        forelagt: Instant? = null,
        vedtaksperiodeId: String = "_",
        behandlingId: String = "_",
    ): ForelagteOpplysningerDbRecord {
        return ForelagteOpplysningerDbRecord(
            id = "test-id",
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            forelagteOpplysningerMelding =
                PGobject().apply {
                    type = "json"
                    value = "{}"
                },
            opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            forelagt = forelagt,
            opprinneligOpprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
        )
    }
}
