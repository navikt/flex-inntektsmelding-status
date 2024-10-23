package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.fnrFlexer
import no.nav.helse.flex.api.FlexInternalFrontendController.HentVedtaksperioderPostRequest
import no.nav.helse.flex.api.FlexInternalFrontendController.VedtakOgInntektsmeldingerResponse
import no.nav.helse.flex.auditlogging.AuditEntry
import no.nav.helse.flex.auditlogging.EventType
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.INNTEKTSMELDING_TOPIC
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.varselutsending.VarselutsendingCronJob
import no.nav.helse.flex.vedtaksperiodebehandling.*
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [Application::class])
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@EnableMockOAuth2Server
abstract class FellesTestOppsett {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var server: MockOAuth2Server

    @Autowired
    lateinit var organisasjonRepository: OrganisasjonRepository

    @Autowired
    lateinit var varselutsendingCronJob: VarselutsendingCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var inntektsmeldingRepository: InntektsmeldingRepository

    @Autowired
    lateinit var vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository

    @Autowired
    lateinit var vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var lockRepository: LockRepository

    @Autowired
    lateinit var meldingKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var varslingConsumer: Consumer<String, String>

    @Autowired
    lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    lateinit var hentAltForPerson: HentAltForPerson

    @Autowired
    lateinit var auditlogKafkaConsumer: Consumer<String, String>

    companion object {
        init {

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).apply {
                start()
                System.setProperty("KAFKA_BROKERS", bootstrapServers)
            }

            PostgreSQLContainer14().apply {
                withCommand("postgres", "-c", "wal_level=logical")
                start()
                System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
                System.setProperty("spring.datasource.username", username)
                System.setProperty("spring.datasource.password", password)
            }
        }
    }

    @AfterAll
    fun `Vi leser oppgave kafka topicet og feil hvis noe finnes og slik at subklassetestene leser alt`() {
        varslingConsumer.hentProduserteRecords().shouldBeEmpty()
        meldingKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser oppgave og done kafka topicet og feiler om noe eksisterer`() {
        varslingConsumer.subscribeHvisIkkeSubscribed(MINSIDE_BRUKERVARSEL)
        meldingKafkaConsumer.subscribeHvisIkkeSubscribed(DITT_SYKEFRAVAER_MELDING_TOPIC)
        varslingConsumer.hentProduserteRecords().shouldBeEmpty()
        meldingKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        auditlogKafkaConsumer.subscribeHvisIkkeSubscribed(AUDIT_TOPIC)
        auditlogKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun forAlleTester() {
        slettFraDatabase()
    }

    fun sendSoknad(soknad: SykepengesoknadDTO) {
        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad.serialisertTilString(),
            ),
        ).get()
    }

    fun sendInntektsmelding(inntektsmelding: Inntektsmelding): RecordMetadata {
        return kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_TOPIC,
                UUID.randomUUID().toString(),
                inntektsmelding.serialisertTilString(),
            ),
        ).get()
    }

    fun sendBehandlingsstatusMelding(behandlingstatusmelding: Behandlingstatusmelding) {
        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                behandlingstatusmelding.vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()
    }

    fun awaitOppdatertStatus(
        forventetSisteSpleisstatus: StatusVerdi,
        vedtaksperiodeId: String = Testdata.vedtaksperiodeId,
        behandlingId: String = Testdata.behandlingId,
    ): VedtaksperiodeBehandlingDbRecord {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == forventetSisteSpleisstatus
            }
        }
        return vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
            vedtaksperiodeId,
            behandlingId,
        )!!
    }

    fun awaitOppdatertStatus(
        forventetSisteSpleisstatus: StatusVerdi,
        forventetSisteVarselstatus: StatusVerdi,
        vedtaksperiodeId: String = Testdata.vedtaksperiodeId,
        behandlingId: String = Testdata.behandlingId,
    ): VedtaksperiodeBehandlingDbRecord {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    Testdata.vedtaksperiodeId,
                    Testdata.behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == forventetSisteSpleisstatus &&
                    vedtaksperiode.sisteVarslingstatus == forventetSisteVarselstatus
            }
        }
        return vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
            Testdata.vedtaksperiodeId,
            Testdata.behandlingId,
        )!!
    }

    fun slettFraDatabase() {
        jdbcTemplate.update("DELETE FROM organisasjon")
        jdbcTemplate.update("DELETE FROM sykepengesoknad")
        jdbcTemplate.update("DELETE FROM inntektsmelding")
        jdbcTemplate.update("DELETE FROM vedtaksperiode_behandling_status")
        jdbcTemplate.update("DELETE FROM vedtaksperiode_behandling_sykepengesoknad")
        jdbcTemplate.update("DELETE FROM vedtaksperiode_behandling")
    }

    fun sendSykepengesoknad(soknad: SykepengesoknadDTO) {
        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                null,
                soknad.id,
                soknad.serialisertTilString(),
            ),
        ).get()
    }

    fun hentVedtaksperioder(fnr: String = Testdata.fnr): List<FullVedtaksperiodeBehandling> {
        val responseString =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/vedtak-og-inntektsmeldinger")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .accept("application/json; charset=UTF-8")
                        .content(
                            HentVedtaksperioderPostRequest(fnr = fnr)
                                .serialisertTilString(),
                        )
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful).andReturn().response.contentAsString

        val response: VedtakOgInntektsmeldingerResponse = objectMapper.readValue(responseString)
        return response.vedtaksperioder
    }

    fun verifiserAuditlogging() {
        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` fnr
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter inntektsmeldinger"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/vedtak-og-inntektsmeldinger")
                requestMethod `should be equal to` "POST"
            }
        }
    }
}

fun String.tilOpprettVarselInstance(): VarselActionBuilder.OpprettVarselInstance {
    return objectMapper.readValue(this)
}

fun String.tilInaktiverVarselInstance(): VarselActionBuilder.InaktiverVarselInstance {
    return objectMapper.readValue(this)
}
