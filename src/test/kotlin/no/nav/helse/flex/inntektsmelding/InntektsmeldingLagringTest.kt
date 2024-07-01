package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.skapInntektsmelding
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.TimeUnit

class InntektsmeldingLagringTest : FellesTestOppsett() {
    @Autowired
    lateinit var producer: KafkaProducer<String, String>

    @BeforeEach
    fun clearDatabase() {
        inntektsmeldingRepository.deleteAll()
    }

    @Test
    fun `Mottar inntektsmeldingmelding med full refusjon`() {
        val fnr = "12345678787"

        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(0)
        produserMelding(
            UUID.randomUUID().toString(),
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = "123456789",
                refusjonBelopPerMnd = BigDecimal(10000),
                beregnetInntekt = BigDecimal(10000),
                vedtaksperiodeId = "ffcf0c28-dd35-4b5d-b518-3d3cbda9329a",
            ).serialisertTilString(),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(1)

        val melding = inntektsmeldingRepository.findByFnrIn(listOf(fnr)).first()
        melding.arbeidsgivertype `should be equal to` "VIRKSOMHET"
        melding.fullRefusjon `should be equal to` true
        melding.vedtaksperiodeId `should be equal to` "ffcf0c28-dd35-4b5d-b518-3d3cbda9329a"
    }

    @Test
    fun `Mottar inntektsmeldingmelding med delvis refusjon`() {
        val fnr = "54545454"

        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(0)
        produserMelding(
            UUID.randomUUID().toString(),
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = "123456789",
                refusjonBelopPerMnd = BigDecimal(5000),
                beregnetInntekt = BigDecimal(10000),
            ).serialisertTilString(),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(1)

        val melding = inntektsmeldingRepository.findByFnrIn(listOf(fnr)).first()
        melding.arbeidsgivertype `should be equal to` "VIRKSOMHET"
        melding.fullRefusjon `should be equal to` false
    }

    @Test
    fun `Mottar inntektsmeldingmelding med null refusjon`() {
        val fnr = "34533452"

        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(0)
        produserMelding(
            UUID.randomUUID().toString(),
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = "123456789",
                refusjonBelopPerMnd = null,
                beregnetInntekt = BigDecimal(10000),
            ).serialisertTilString(),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(1)

        val melding = inntektsmeldingRepository.findByFnrIn(listOf(fnr)).first()
        melding.arbeidsgivertype `should be equal to` "VIRKSOMHET"
        melding.fullRefusjon `should be equal to` false
    }

    fun produserMelding(
        meldingUuid: String,
        melding: String,
    ): RecordMetadata {
        return producer.send(
            ProducerRecord(
                INNTEKTSMELDING_TOPIC,
                meldingUuid,
                melding,
            ),
        ).get()
    }
}
