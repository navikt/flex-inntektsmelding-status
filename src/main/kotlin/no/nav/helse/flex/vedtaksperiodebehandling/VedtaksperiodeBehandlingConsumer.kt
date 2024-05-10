package no.nav.helse.flex.vedtaksperiodebehandling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.util.EnvironmentToggles
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class VedtaksperiodeBehandlingConsumer(
    private val prosseserKafkaMeldingFraSpleiselaget: ProsseserKafkaMeldingFraSpleiselaget,
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

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
        val kafkaDto: Behandlingstatusmelding = objectMapper.readValue(cr.value())

        try {
            prosseserKafkaMeldingFraSpleiselaget.prosesserKafkaMelding(kafkaDto)
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
