package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.api.FlexInternalFrontendController
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagtStatus
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerMelding
import no.nav.helse.flex.organisasjon.Organisasjon
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.vedtaksperiodebehandling.SpleisStatus
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadDbRecord
import org.amshove.kluent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.postgresql.util.PGobject
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FlexInternalFrontendApiAuthTest : FellesTestOppsett() {
    @AfterEach
    fun rensDb() {
        super.slettFraDatabase()
    }

    @Test
    fun `Trenger riktig clientid for å hente data med api`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/vedtak-og-inntektsmeldinger")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .content(FlexInternalFrontendController.HentVedtaksperioderPostRequest(fnr = fnr).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Trenger auth header for å hente data med api`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/vedtak-og-inntektsmeldinger")
                    .content(FlexInternalFrontendController.HentVedtaksperioderPostRequest(fnr = fnr).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `forelagte opplysninger blir sendt som strukturert json`() {
        lagreForelagteOpplysningerMedTilhorendeData()

        val responseString =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/vedtak-og-inntektsmeldinger")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .accept("application/json; charset=UTF-8")
                        .content(FlexInternalFrontendController.HentVedtaksperioderPostRequest(fnr = fnr).serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
                .andReturn()
                .response.contentAsString

        val response: FlexInternalFrontendController.VedtakOgInntektsmeldingerResponse = objectMapper.readValue(responseString)
        response.forelagteOpplysninger.shouldHaveSize(1)

        val forelagteOpplysninger = response.forelagteOpplysninger.single()
        val melding = forelagteOpplysninger.forelagteOpplysningerMelding
        melding!!.omregnetÅrsinntekt `should be equal to` 123456.0
        melding.skatteinntekter.shouldHaveSize(2)
        melding.skatteinntekter.first().måned `should be equal to` YearMonth.of(2024, 1)

        verifiserAuditlogging()
    }

    private fun lagreForelagteOpplysningerMedTilhorendeData(
        sykepengesoknadUuid: String = UUID.randomUUID().toString(),
        personFnr: String = fnr,
        orgnummer: String = "test-org",
        vedtaksperiodeId: String = UUID.randomUUID().toString(),
        behandlingId: String = UUID.randomUUID().toString(),
    ) {
        forelagteOpplysningerRepository.save(
            ForelagteOpplysningerDbRecord(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                forelagteOpplysningerMelding =
                    PGobject().apply {
                        type = "json"
                        value =
                            ForelagteOpplysningerMelding(
                                vedtaksperiodeId = vedtaksperiodeId,
                                behandlingId = behandlingId,
                                tidsstempel = LocalDateTime.parse("2024-01-01T00:00:00.00"),
                                omregnetÅrsinntekt = 123456.0,
                                skatteinntekter =
                                    listOf(
                                        ForelagteOpplysningerMelding.Skatteinntekt(
                                            måned = YearMonth.of(2024, 1),
                                            beløp = 42000.0,
                                        ),
                                        ForelagteOpplysningerMelding.Skatteinntekt(
                                            måned = YearMonth.of(2024, 2),
                                            beløp = 43000.0,
                                        ),
                                    ),
                            ).serialisertTilString()
                    },
                opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
                opprinneligOpprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
                status = ForelagtStatus.NY,
                statusEndret = Instant.parse("2024-01-01T00:00:00.00Z"),
            ),
        )

        val soknad =
            Sykepengesoknad(
                sykepengesoknadUuid = sykepengesoknadUuid,
                orgnummer = orgnummer,
                soknadstype = "ARBEIDSTAKER",
                startSyketilfelle = LocalDate.of(2024, 1, 1),
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 16),
                fnr = personFnr,
                sendt = Instant.parse("2024-01-16T00:00:00.00Z"),
                opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
            ).also {
                sykepengesoknadRepository.save(it)
            }

        val vedtaksperiodeBehandling =
            vedtaksperiodeBehandlingRepository.save(
                VedtaksperiodeBehandlingDbRecord(
                    opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    oppdatertDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteSpleisstatus = SpleisStatus.VENTER_PÅ_ARBEIDSGIVER,
                    sisteSpleisstatusTidspunkt = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteVarslingstatus = null,
                    sisteVarslingstatusTidspunkt = null,
                    vedtaksperiodeId = vedtaksperiodeId,
                    behandlingId = behandlingId,
                ),
            )

        vedtaksperiodeBehandlingSykepengesoknadRepository.save(
            VedtaksperiodeBehandlingSykepengesoknadDbRecord(
                vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id!!,
                sykepengesoknadUuid = soknad.sykepengesoknadUuid,
            ),
        )

        Organisasjon(
            orgnummer = orgnummer,
            navn = "Organisasjonen",
            opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            oppdatert = Instant.parse("2024-01-01T00:00:00.00Z"),
            oppdatertAv = "personen",
        ).also {
            if (organisasjonRepository.findByOrgnummer(orgnummer) == null) {
                organisasjonRepository.save(it)
            }
        }
    }
}
