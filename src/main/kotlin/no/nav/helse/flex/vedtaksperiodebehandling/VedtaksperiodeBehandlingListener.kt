package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class VedtaksperiodeBehandlingListener {
    val log = logger()

    @KafkaListener(
        topics = [SIS_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "flex-inntektsmelding-status-vedtaksperiode-behandling",
        idIsGroup = false,
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("Holder offset up to date imens reprosessering kjører")

        acknowledgment.acknowledge()
    }
}
