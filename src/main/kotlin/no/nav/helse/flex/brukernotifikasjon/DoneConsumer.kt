package no.nav.helse.flex.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.helse.flex.kafka.brukernotifikasjonDoneTopic
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("brukernot")
class DoneConsumer {

    val log = logger()

    @KafkaListener(
        topics = [brukernotifikasjonDoneTopic],
        containerFactory = "avroAivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
        id = "flex-inntektsmelding-status-done",
        idIsGroup = false,
    )
    fun listen(cr: ConsumerRecord<NokkelInput, DoneInput>, acknowledgment: Acknowledgment) {
        prosesserKafkaMelding(cr.key(), cr.value())

        acknowledgment.acknowledge()
    }

    fun prosesserKafkaMelding(
        key: NokkelInput,
        value: DoneInput,
    ) {
        log.info("flex-inntektsmelding-status-done: $value")
    }
}
