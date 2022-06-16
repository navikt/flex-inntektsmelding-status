package no.nav.helse.flex.melding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.dittSykefravaerMeldingTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class MeldingConsumer {

    val log = logger()

    @KafkaListener(
        topics = [dittSykefravaerMeldingTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "ditt-sykefravaer-melding",
        idIsGroup = false,
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
