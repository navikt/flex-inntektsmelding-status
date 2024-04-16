package no.nav.helse.flex.testdata

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.INNTEKTSMELDING_STATUS_TESTDATA_TOPIC
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.vedtaksperiode.InntektsmeldingKafkaDto
import no.nav.helse.flex.vedtaksperiode.VedtaksperiodeStatusService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("testdata")
class InntektsmeldingConsumerTestdata(
    private val vedtaksperiodeStatusService: VedtaksperiodeStatusService,
) {
    @KafkaListener(
        topics = [INNTEKTSMELDING_STATUS_TESTDATA_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "flex-inntektsmelding-status-inntektsmelding-testdata",
        idIsGroup = false,
    )
    fun listenToTest(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val kafkaDto: InntektsmeldingKafkaDto = objectMapper.readValue(cr.value())

        vedtaksperiodeStatusService.prosesserKafkaMelding(kafkaDto)

        acknowledgment.acknowledge()
    }
}
