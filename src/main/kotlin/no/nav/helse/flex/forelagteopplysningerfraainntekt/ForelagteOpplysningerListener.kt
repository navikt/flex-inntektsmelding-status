package no.nav.helse.flex.forelagteopplysningerfraainntekt

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class InntektsmeldingListener(
) {
    val log = logger()
    @KafkaListener(
        topics = [FORELAGTE_OPPLYSNINGER_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("motatt melding: " + cr.value())
        acknowledgment.acknowledge()
    }
}

const val FORELAGTE_OPPLYSNINGER_TOPIC = "tbd.forelagte-opplysninger"

