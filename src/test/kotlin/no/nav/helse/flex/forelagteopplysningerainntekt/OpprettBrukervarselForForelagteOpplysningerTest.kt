package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import org.amshove.kluent.`should be equal to`
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
            now = Instant.parse("2022-06-16T00:00:00.00Z"),
            startSyketilfelle = LocalDate.parse("2022-06-16"),
            dryRun = false,
        )

        verify(mockBrukervarsel).beskjedForelagteOpplysninger(
            eq("test-fnr"),
            eq("test-id"),
            any(),
            eq(LocalDateTime.parse("2022-06-16T00:00:00.00").plusWeeks(3).toInstant(ZoneOffset.UTC)),
            eq("https://test.test/test-id"),
        )
        verify(mockMeldingKafkaProducer).produserMelding(eq("test-id"), any())
    }
}
