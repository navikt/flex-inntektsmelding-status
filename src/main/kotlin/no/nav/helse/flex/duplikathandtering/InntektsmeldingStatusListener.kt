package no.nav.helse.flex.duplikathandtering

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.kafka.inntektsmeldingstatusTopic
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset

@Component
class InntektsmeldingStatusListener(
    private val duplikathandtering: Duplikathandtering
) : ConsumerSeekAware {

    @KafkaListener(
        topics = [inntektsmeldingstatusTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "flex-inntektsmelding-status-inntektsmelding-duplikathandtering",
        idIsGroup = false,
        groupId = "duplikathandtering-dryrun"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val kafkaDto: InntektsmeldingKafkaDto = objectMapper.readValue(cr.value())

        duplikathandtering.prosesserKafkaMelding(kafkaDto)

        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: MutableMap<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback
    ) {
        val startDateInEpochMilli = LocalDate.of(2022, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        callback.seekToTimestamp(assignments.map { it.key }, startDateInEpochMilli)
    }
}
