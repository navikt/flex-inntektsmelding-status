package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.varseltekst.skapForelagteOpplysningerTekst
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.postgresql.util.PGobject
import java.time.*

class OpprettBrukervarselForForelagteOpplysningerTest {
    @Test
    fun `burde konvertere fra PGobject til JsonNode`() {
        val pGobject =
            PGobject().apply {
                type = "json"
                value =
                    ForelagteOpplysningerMelding(
                        vedtaksperiodeId = "id",
                        behandlingId = "id",
                        tidsstempel = LocalDateTime.parse("2022-06-16T00:00:00"),
                        omregnetÅrsinntekt = 780_000.0,
                        skatteinntekter =
                            listOf(
                                ForelagteOpplysningerMelding.Skatteinntekt(
                                    YearMonth.of(2024, 1),
                                    40_000.0,
                                ),
                            ),
                    ).serialisertTilString()
            }
        val jsonNode = forelagtOpplysningTilMetadata(pGobject, "Snekkeri AS")
        val aaregInntekt: AaregInntekt = objectMapper.convertValue(jsonNode, AaregInntekt::class.java)
        aaregInntekt `should be equal to`
            AaregInntekt(
                tidsstempel = LocalDateTime.parse("2022-06-16T00:00:00"),
                inntekter = listOf(AaregInntekt.Inntekt("2024-01", 40_000.0)),
                omregnetAarsinntekt = 780_000.0,
                orgnavn = "Snekkeri AS",
            )
    }

    @Test
    fun `opprettet melding burde ha riktig metadata`() {
        val mockBrukervarsel: Brukervarsel = Mockito.mock(Brukervarsel::class.java)
        val mockMeldingKafkaProducer: MeldingKafkaProducer = Mockito.mock(MeldingKafkaProducer::class.java)

        val opprettMeldingForForelagtOpplysning =
            OpprettBrukervarselForForelagteOpplysninger(
                brukervarsel = mockBrukervarsel,
                meldingKafkaProducer = mockMeldingKafkaProducer,
                "https://test.test",
            )

        opprettMeldingForForelagtOpplysning.opprettVarslinger(
            varselId = "test-id",
            melding =
                PGobject().apply {
                    type = "json"
                    value =
                        ForelagteOpplysningerMelding(
                            vedtaksperiodeId = "id",
                            behandlingId = "id",
                            tidsstempel = LocalDateTime.parse("2022-06-16T00:00:00"),
                            omregnetÅrsinntekt = 780_000.0,
                            skatteinntekter =
                                listOf(
                                    ForelagteOpplysningerMelding.Skatteinntekt(
                                        YearMonth.of(2024, 1),
                                        40_000.0,
                                    ),
                                ),
                        ).serialisertTilString()
                },
            fnr = "test-fnr",
            orgNavn = "test-orgnavn",
            startSyketilfelle = LocalDate.parse("2022-06-16"),
            opprinneligOpprettet = Instant.parse("2022-06-16T00:00:00.00Z"),
        )

        val startSykeTilfelle = LocalDateTime.parse("2022-06-16T00:00:00.00")
        val treUkerFrem = startSykeTilfelle.plusWeeks(3)
        verify(mockBrukervarsel).beskjedForelagteOpplysninger(
            eq("test-fnr"),
            eq("test-id"),
            eq(treUkerFrem.toInstant(ZoneOffset.UTC)),
            eq("https://test.test/test-id"),
            eq(skapForelagteOpplysningerTekst()),
        )
        verify(mockMeldingKafkaProducer).produserMelding(eq("test-id"), any())
    }

    @Test
    fun `brukervarsel burde ha riktig tekst`() {
        val mockBrukervarsel = mock<Brukervarsel>()

        val opprettMeldingForForelagtOpplysning =
            OpprettBrukervarselForForelagteOpplysninger(
                brukervarsel = mockBrukervarsel,
                meldingKafkaProducer = mock<MeldingKafkaProducer>(),
                "https://test.test",
            )

        opprettMeldingForForelagtOpplysning.opprettVarslinger(
            varselId = "_",
            melding = lagMelding(),
            fnr = "_",
            orgNavn = "_",
            startSyketilfelle = LocalDate.parse("2022-06-16"),
            opprinneligOpprettet = anyInstant(),
        )

        verify(mockBrukervarsel).beskjedForelagteOpplysninger(
            fnr = any(),
            bestillingId = any(),
            synligFremTil = any(),
            lenke = any(),
            varselTekst =
                eq(
                    "Status i saken din om sykepenger: Vi har hentet opplysninger om inntekten din fra a-ordningen for sykefraværet." +
                        "Vi trenger at du sjekker om de stemmer.",
                ),
        )
    }

    @Test
    fun `min side varsel burde ha riktig tekst`() {
        val meldingKafkaProducer = mock<MeldingKafkaProducer>()

        val opprettMeldingForForelagtOpplysning =
            OpprettBrukervarselForForelagteOpplysninger(
                brukervarsel = mock<Brukervarsel>(),
                meldingKafkaProducer = meldingKafkaProducer,
                "https://test.test",
            )

        opprettMeldingForForelagtOpplysning.opprettVarslinger(
            varselId = "_",
            melding = lagMelding(),
            fnr = "_",
            orgNavn = "_",
            startSyketilfelle = LocalDate.parse("2022-06-16"),
            opprinneligOpprettet = anyInstant(),
        )

        val argCaptor = argumentCaptor<MeldingKafkaDto>()
        verify(meldingKafkaProducer).produserMelding(
            meldingUuid = any(),
            meldingKafkaDto = argCaptor.capture(),
        )

        argCaptor.firstValue.opprettMelding
            .shouldNotBeNull()
            .tekst `should be equal to` "Status i saken din om sykepenger: " +
            "Vi har hentet opplysninger om inntekten din fra a-ordningen for sykefraværet." +
            "Vi trenger at du sjekker om de stemmer."
    }

    private fun lagMelding(): PGobject {
        val melding =
            ForelagteOpplysningerMelding(
                vedtaksperiodeId = "vedtaksperiodeId",
                behandlingId = "behandlingId",
                tidsstempel = LocalDateTime.parse("2022-06-16T00:00:00"),
                omregnetÅrsinntekt = 780_000.0,
                skatteinntekter =
                    listOf(
                        ForelagteOpplysningerMelding.Skatteinntekt(
                            YearMonth.of(2024, 1),
                            40_000.0,
                        ),
                    ),
            )

        return PGobject().apply {
            type = "json"
            value = melding.serialisertTilString()
        }
    }

    private fun anyInstant() = Instant.parse("2022-06-16T00:00:00.00Z")
}
