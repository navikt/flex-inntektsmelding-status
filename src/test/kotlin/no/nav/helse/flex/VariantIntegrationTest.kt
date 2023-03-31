package no.nav.helse.flex

import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusDbRecord
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.kafka.dittSykefravaerMeldingTopic
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

private const val FNR_1 = "fnr-1"

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VariantIntegrationTest : FellesTestOppsett() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @BeforeEach
    fun opprettMeldinger() {
        InntektsmeldingDbRecord(
            fnr = FNR_1,
            orgNr = "orgNr-1",
            orgNavn = "orgNavn-1",
            opprettet = Instant.now(),
            vedtakFom = LocalDate.now(),
            vedtakTom = LocalDate.now(),
            eksternTimestamp = Instant.now(),
            eksternId = "eksternId-1"
        ).let {
            inntektsmeldingRepository.save(it)
        }

        InntektsmeldingStatusDbRecord(
            inntektsmeldingId = inntektsmeldingRepository.findAll().first().id!!,
            opprettet = Instant.now(),
            status = StatusVerdi.DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT
        ).let {
            inntektsmeldingStatusRepository.save(it)
        }
    }

    @AfterEach
    fun slettMeldinger() {
        slettFraDatabase()
    }

    @Test
    @Order(1)
    fun `Sender opprettMelding med upper-case Variant`() {
        val kafkaMeldingSomString =
            "{\"opprettMelding\":{\"tekst\":\"Melding\",\"lenke\":\"https://www.nav.no\",\"variant\":\"INFO\",\"lukkbar\":true,\"meldingType\":\"meldingType\",\"synligFremTil\":\"${
            Instant.now().plus(2, ChronoUnit.DAYS)
            }\"},\"lukkMelding\":{\"timestamp\":\"${Instant.now()}\"},\"fnr\":\"$FNR_1\"}"

        val forrigeStatus = inntektsmeldingStatusRepository.findAll().first()

        kafkaProducer.send(
            ProducerRecord(
                dittSykefravaerMeldingTopic,
                forrigeStatus.id,
                kafkaMeldingSomString
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.count() == 2L
        }

        meldingKafkaConsumer.ventPåRecords(1).first().key() shouldBeEqualTo forrigeStatus.id
    }

    @Test
    @Order(2)
    fun `Sender opprettMelding med lower-case Variant`() {
        val kafkaMeldingSomString =
            "{\"opprettMelding\":{\"tekst\":\"Melding\",\"lenke\":\"https://www.nav.no\",\"variant\":\"info\",\"lukkbar\":true,\"meldingType\":\"meldingType\",\"synligFremTil\":\"${
            Instant.now().plus(2, ChronoUnit.DAYS)
            }\"},\"lukkMelding\":{\"timestamp\":\"${Instant.now()}\"},\"fnr\":\"$FNR_1\"}"

        val forrigeStatus = inntektsmeldingStatusRepository.findAll().first()

        kafkaProducer.send(
            ProducerRecord(
                dittSykefravaerMeldingTopic,
                forrigeStatus.id,
                kafkaMeldingSomString
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.count() == 2L
        }

        meldingKafkaConsumer.ventPåRecords(1).first().key() shouldBeEqualTo forrigeStatus.id
    }
}
