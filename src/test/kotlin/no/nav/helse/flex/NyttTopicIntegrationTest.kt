package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.FERDIG
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.VENTER_PÅ_ARBEIDSGIVER
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Disabled
class NyttTopicIntegrationTest : FellesTestOppsett() {
    private final val fnr = "12345678901"
    private final val vedtaksperiodeId = UUID.randomUUID().toString()
    private final val behandlingId = UUID.randomUUID().toString()
    private final val soknadId = UUID.randomUUID().toString()
    private final val orgNr = "123456547"
    private final val fom = LocalDate.of(2022, 6, 1)
    private final val tom = LocalDate.of(2022, 6, 30)

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()
        val soknad =
            SykepengesoknadDTO(
                fnr = fnr,
                id = soknadId,
                type = SoknadstypeDTO.ARBEIDSTAKERE,
                status = SoknadsstatusDTO.NY,
                startSyketilfelle = fom,
                fom = fom,
                tom = tom,
                arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
                arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr),
            )

        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad
                    .copy(
                        status = SoknadsstatusDTO.SENDT,
                        sendtNav = LocalDateTime.now(),
                    )
                    .serialisertTilString(),
            ),
        ).get()
        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad.serialisertTilString(),
            ),
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at perioden venter på arbeidsgiver`() {
        periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()

        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            )
        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()
        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.copy(
                    status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
                ).serialisertTilString(),
            ),
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == VENTER_PÅ_ARBEIDSGIVER
            }
        }
        val perioderSomVenterPaaArbeidsgiver =
            periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first().fnr shouldBeEqualTo fnr
        perioderSomVenterPaaArbeidsgiver.first().sykepengesoknadUuid shouldBeEqualTo soknadId

        periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(OffsetDateTime.now().minusHours(3).toInstant())
            .shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `Vi kan hente ut historikken fra flex internal frontend`() {
        val responseString =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/vedtaksperioder")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .accept("application/json; charset=UTF-8")
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful).andReturn().response.contentAsString

        val response: List<FullVedtaksperiodeBehandling> = objectMapper.readValue(responseString)
        response shouldHaveSize 1
        response[0].soknader.first().orgnummer shouldBeEqualTo orgNr
        // response[0].vedtaksperioder[0].status shouldHaveSize 2
        response[0].statuser shouldHaveSize 2
        response[0].vedtaksperiode.sisteSpleisstatus shouldBeEqualTo VENTER_PÅ_ARBEIDSGIVER
    }

    @Test
    @Order(3)
    fun `Vi får beskjed at perioden venter på saksbehandling`() {
        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            ) // eksternSøknadId = soknadId,

        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_SAKSBEHANDLER
            }
        }
    }

    @Test
    @Order(4)
    fun `Vi får beskjed at perioden er ferdig`() {
        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.FERDIG,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            )
        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == FERDIG
            }
        }
    }

    @Test
    @Order(5)
    fun `Vi kan hente ut historikken fra flex internal frontend igjen`() {
        val responseString =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/vedtaksperioder")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .accept("application/json; charset=UTF-8")
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful).andReturn().response.contentAsString

        val response: List<FullVedtaksperiodeBehandling> = objectMapper.readValue(responseString)
        response shouldHaveSize 1
        response.first().soknader.first().orgnummer shouldBeEqualTo orgNr
        // response[0].soknad.orgnummer shouldBeEqualTo orgNr
        response.first().statuser shouldHaveSize 4
        // response[0].vedtaksperioder[0].status shouldHaveSize 4

        response.first().statuser.map { it.status.name } shouldBeEqualTo
            listOf(
                "OPPRETTET",
                "VENTER_PÅ_ARBEIDSGIVER",
                "VENTER_PÅ_SAKSBEHANDLER",
                "FERDIG",
            )

//        response[0].vedtaksperioder[0].status.map { it.status.name } shouldBeEqualTo
//            listOf(
//                "OPPRETTET",
//                "VENTER_PÅ_ARBEIDSGIVER",
//                "VENTER_PÅ_SAKSBEHANDLER",
//                "FERDIG",
//            )

        response.first().vedtaksperiode.sisteSpleisstatus shouldBeEqualTo FERDIG
        // response[0].vedtaksperioder[0].vedtaksperiode.sisteSpleisstatus shouldBeEqualTo FERDIG
    }

    @Test
    @Order(99)
    fun `Trenger riktig auth for å hente data med api`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/vedtaksperioder")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)

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
