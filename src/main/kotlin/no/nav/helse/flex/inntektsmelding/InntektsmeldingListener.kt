package no.nav.helse.flex.inntektsmelding

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class InntektsmeldingListener(
    val lagreInntektsmeldingerFraKafka: LagreInntektsmeldingerFraKafka,
) {
    @KafkaListener(
        topics = [INNTEKTSMELDING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        lagreInntektsmeldingerFraKafka.oppdater(cr.value())
        acknowledgment.acknowledge()
    }
}

const val INNTEKTSMELDING_TOPIC = "helsearbeidsgiver." + "inntektsmelding-bruker"
