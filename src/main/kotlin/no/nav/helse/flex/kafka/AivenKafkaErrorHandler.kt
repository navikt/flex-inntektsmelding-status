package no.nav.helse.flex.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import org.springframework.util.backoff.ExponentialBackOff
import no.nav.helse.flex.logger as slf4jLogger

@Component
class AivenKafkaErrorHandler : DefaultErrorHandler(
    null,
    ExponentialBackOff(1000L, 1.5).also {
        // 8 minutter, som er mindre enn max.poll.interval.ms på 10 minutter.
        it.maxInterval = 60_000L * 8
    },
) {
    // Bruker aliased logger for unngå kollisjon med CommonErrorHandler.logger(): LogAccessor.
    val log = slf4jLogger()

    override fun handleRemaining(
        thrownException: Exception,
        records: MutableList<ConsumerRecord<*, *>>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
    ) {
        if (records.isEmpty()) {
            log.error("Listener mottok ikke data.", thrownException)
        }
        records.forEach { record ->
            loggFeilMedMaskerFnr(record, thrownException)
        }
        super.handleRemaining(thrownException, records, consumer, container)
    }

    override fun handleBatch(
        thrownException: Exception,
        data: ConsumerRecords<*, *>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
        invokeListener: Runnable,
    ) {
        if (data.isEmpty) {
            log.error("Listener mottok ikke data.", thrownException)
        }
        data.forEach { record ->
            loggFeilMedMaskerFnr(record, thrownException)
        }
        super.handleBatch(thrownException, data, consumer, container, invokeListener)
    }

    private fun loggFeilMedMaskerFnr(
        record: ConsumerRecord<*, *>,
        thrownException: Exception,
    ) {
        val key =
            if (record.key().erFnr()) {
                "***"
            } else {
                record.key()
            }
        log.error(
            "Feil i prossesseringen av record med offset: ${record.offset()}, key: $key på topic ${record.topic()}.",
            thrownException,
        )
    }

    private fun Any.erFnr() = this.toString().length == 11
}
