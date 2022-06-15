package no.nav.helse.flex.testdata

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.inntektsmelding.InntekstmeldingService
import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.kafka.inntektsmeldingstatusTestdataTopic
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("testdata")
class InntektsmeldingConsumerTestdata(
    private val inntekstmeldingService: InntekstmeldingService
) {
    @KafkaListener(
        topics = [inntektsmeldingstatusTestdataTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "flex-inntektsmelding-status-inntektsmelding-testdata",
        idIsGroup = false,
    )
    fun listenToTest(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val kafkaDto: InntektsmeldingKafkaDto = objectMapper.readValue(cr.value())

        inntekstmeldingService.prosesserKafkaMelding(kafkaDto)

        acknowledgment.acknowledge()
    }
}
