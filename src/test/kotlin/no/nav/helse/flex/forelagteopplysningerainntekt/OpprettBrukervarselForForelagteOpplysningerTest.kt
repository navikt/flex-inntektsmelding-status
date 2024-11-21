package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.postgresql.util.PGobject
import java.time.LocalDateTime
import java.time.YearMonth

class OpprettBrukervarselForForelagteOpplysningerTest {
    @Test
    fun `burde mappe fra PGobject til JsonNode`() {
        val pGobject =
            PGobject().apply {
                type = "json"
                value =
                    ForelagteOpplysningerMelding(
                        vedtaksperiodeId = "id",
                        behandlingId = "id",
                        tidsstempel = LocalDateTime.parse("2022-06-16T00:00:00"),
                        omregnetÅrsinntekt = 780_000.0,
                        skatteinntekter = listOf(ForelagteOpplysningerMelding.Skatteinntekt(YearMonth.of(2024, 1), 40_000.0)),
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

        val opprettMeldingForForelagtOpplysning = OpprettBrukervarselForForelagteOpplysninger(
            brukervarsel = mockBrukervarsel,
            meldingKafkaProducer = mockMeldingKafkaProducer,
            "https://test.test"
        )

        opprettMeldingForForelagtOpplysning.opprettVarslinger(
            varselId = "test-id",
            melding = TODO(),
            fnr = TODO(),
            orgNavn = TODO(),
            now = TODO(),
            dryRun = TODO()
        )
    }
}
