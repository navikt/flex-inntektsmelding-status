package no.nav.helse.flex.inntektsmelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.bomloInntektsmeldingManglerTopic
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("bomlo")
class InntektsmeldingConsumer(
    private val inntektsmeldingService: InntektsmeldingService
) {
    @KafkaListener(
        topics = [bomloInntektsmeldingManglerTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "flex-inntektsmelding-status-inntektsmelding",
        idIsGroup = false,
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val kafkaDto: InntektsmeldingKafkaDto = objectMapper.readValue(cr.value())

        inntektsmeldingService.prosesserKafkaMelding(kafkaDto)

        acknowledgment.acknowledge()
    }
}
