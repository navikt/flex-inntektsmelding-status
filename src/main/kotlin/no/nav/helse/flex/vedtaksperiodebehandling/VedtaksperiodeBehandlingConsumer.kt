package no.nav.helse.flex.vedtaksperiodebehandling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class VedtaksperiodeBehandlingConsumer(
    private val prosseserKafkaMeldingFraSpleiselaget: ProsseserKafkaMeldingFraSpleiselaget,
) : ConsumerSeekAware {
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
        val versjonsmelding: MeldingMedVersjon = objectMapper.readValue(cr.value())

        if (versjonsmelding.versjon == "2.0.0") {
            val kafkaDto: Behandlingstatusmelding = objectMapper.readValue(cr.value())
            prosseserKafkaMeldingFraSpleiselaget.prosesserKafkaMelding(kafkaDto)
        }

        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: Map<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback,
    ) {
        val timestampToSearch = Instant.now().minus(Duration.ofDays(21)).toEpochMilli()
        val offsetsForTimes = assignments.keys.associateWith { timestampToSearch }

        log.info("Seeking to timestamp $timestampToSearch for partitions $assignments")
        // Seek to the offsets for 3 weeks ago for each partition
        offsetsForTimes.forEach { (partition, timestamp) ->
            log.info("Seeking to timestamp $timestamp for partition $partition")
            callback.seekToTimestamp(partition.topic(), partition.partition(), timestamp)
        }
    }
}
