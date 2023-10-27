package no.nav.helse.flex.inntektsmelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.inntektsmeldingstatusTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.util.EnvironmentToggles
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class InntektsmeldingConsumer(
    private val inntektsmeldingService: InntektsmeldingService,
    private val environmentToggles: EnvironmentToggles
) {
    private val log = logger()

    @KafkaListener(
        topics = [inntektsmeldingstatusTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "flex-inntektsmelding-status-inntektsmelding",
        idIsGroup = false
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val kafkaDto: InntektsmeldingKafkaDto = objectMapper.readValue(cr.value())

        try {
            inntektsmeldingService.prosesserKafkaMelding(kafkaDto)
        } catch (e: RuntimeException) {
            if (!environmentToggles.isProduction() && e.message?.contains("Finner ikke orgnummer") == true) {
                log.info("Finner ikke orgnummer i dev, går videre", e)
            } else {
                throw e
            }
        }

        acknowledgment.acknowledge()
    }
}
