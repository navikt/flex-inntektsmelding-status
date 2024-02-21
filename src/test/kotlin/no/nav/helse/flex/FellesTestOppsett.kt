package no.nav.helse.flex

import no.nav.helse.flex.cronjob.BestillBeskjedJobb
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusRepository
import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.kafka.DITT_SYKEFRAVAER_MELDING_TOPIC
import no.nav.helse.flex.kafka.MINSIDE_BRUKERVARSEL
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.concurrent.thread

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [Application::class])
abstract class FellesTestOppsett {
    @Autowired
    lateinit var inntektsmeldingRepository: InntektsmeldingRepository

    @Autowired
    lateinit var inntektsmeldingStatusRepository: InntektsmeldingStatusRepository

    @Autowired
    lateinit var statusRepository: StatusRepository

    @Autowired
    lateinit var organisasjonRepository: OrganisasjonRepository

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
        jdbcTemplate.update("DELETE FROM inntektsmelding_status")
        jdbcTemplate.update("DELETE FROM inntektsmelding")
    }
}
