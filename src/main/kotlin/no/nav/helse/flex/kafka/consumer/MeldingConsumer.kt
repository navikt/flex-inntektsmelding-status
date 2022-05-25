package no.nav.helse.flex.kafka.consumer

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val MELDING_TOPIC = "flex.ditt-sykefravaer-melding"

@Component
class MeldingConsumer() {

    val log = logger()

    @KafkaListener(
        topics = [MELDING_TOPIC],
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "ditt-sykefravaer-melding",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        prosesserKafkaMelding(cr.key(), cr.value())

        acknowledgment.acknowledge()
    }

    fun prosesserKafkaMelding(
        key: String,
        value: String,
    ) {
        val meldingKafkaDto: MeldingKafkaDto = objectMapper.readValue(value)

        log.info("ditt-sykefravaer-melding: $meldingKafkaDto")
    }
}
