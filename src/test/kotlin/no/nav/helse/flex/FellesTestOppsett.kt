package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.INNTEKTSMELDING_TOPIC
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.kafka.DITT_SYKEFRAVAER_MELDING_TOPIC
import no.nav.helse.flex.kafka.MINSIDE_BRUKERVARSEL
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.varselutsending.VarselutsendingCronJob
import no.nav.helse.flex.vedtaksperiodebehandling.*
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.tms.varsel.builder.VarselActionBuilder
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

    companion object {
        init {
            val threads = mutableListOf<Thread>()

            thread {
                KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3")).apply {
                    start()
                    System.setProperty("KAFKA_BROKERS", bootstrapServers)
                }
            }.also { threads.add(it) }

            thread {
                PostgreSQLContainer14().apply {
                    // Cloud SQL har wal_level = 'logical' på grunn av flagget cloudsql.logical_decoding i
                    // naiserator.yaml. Vi må sette det samme lokalt for at flyway migrering skal fungere.
                    withCommand("postgres", "-c", "wal_level=logical")
                    start()
                    System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
                    System.setProperty("spring.datasource.username", username)
                    System.setProperty("spring.datasource.password", password)
                }
            }.also { threads.add(it) }

            threads.forEach { it.join() }
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
                    Testdata.vedtaksperiodeId,
                    Testdata.behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == forventetSisteSpleisstatus
            }
        }
        return vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
            Testdata.vedtaksperiodeId,
            Testdata.behandlingId,
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
}

fun String.tilOpprettVarselInstance(): VarselActionBuilder.OpprettVarselInstance {
    return objectMapper.readValue(this)
}

fun String.tilInaktiverVarselInstance(): VarselActionBuilder.InaktiverVarselInstance {
    return objectMapper.readValue(this)
}
