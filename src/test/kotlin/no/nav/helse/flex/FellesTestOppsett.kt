package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.cronjob.BestillBeskjedJobb
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.kafka.DITT_SYKEFRAVAER_MELDING_TOPIC
import no.nav.helse.flex.kafka.MINSIDE_BRUKERVARSEL
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.vedtaksperiode.StatusRepository
import no.nav.helse.flex.vedtaksperiode.VedtaksperiodeRepository
import no.nav.helse.flex.vedtaksperiode.VedtaksperiodeStatusRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusRepository
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
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
import kotlin.concurrent.thread

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [Application::class])
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@EnableMockOAuth2Server
abstract class FellesTestOppsett {
    @Autowired
    lateinit var vedtaksperiodeRepository: VedtaksperiodeRepository

    @Autowired
    lateinit var vedtaksperiodeStatusRepository: VedtaksperiodeStatusRepository

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var server: MockOAuth2Server

    @Autowired
    lateinit var statusRepository: StatusRepository

    @Autowired
    lateinit var organisasjonRepository: OrganisasjonRepository

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository

    @Autowired
    lateinit var vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var lockRepository: LockRepository

    @Autowired
    lateinit var bestillBeskjedJobb: BestillBeskjedJobb

    @Autowired
    lateinit var meldingKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var varslingConsumer: Consumer<String, String>

    @Autowired
    lateinit var kafkaProducer: Producer<String, String>

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

    fun slettFraDatabase() {
        jdbcTemplate.update("DELETE FROM vedtaksperiode_status")
        jdbcTemplate.update("DELETE FROM vedtaksperiode")
        jdbcTemplate.update("DELETE FROM organisasjon")
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
