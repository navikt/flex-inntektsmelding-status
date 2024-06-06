package no.nav.helse.flex

import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import org.amshove.kluent.*
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FlexInternalFrontendApiAuthTest : FellesTestOppsett() {
    @Test
    fun `Trenger riktig clientid for å hente data med api`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/vedtaksperioder")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Trenger  auth header for å hente data med api`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/vedtaksperioder")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }
}
