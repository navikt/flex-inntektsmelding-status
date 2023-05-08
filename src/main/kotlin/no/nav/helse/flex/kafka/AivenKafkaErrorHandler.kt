package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import org.springframework.util.backoff.ExponentialBackOff
import java.lang.Exception

@Component
class AivenKafkaErrorHandler : DefaultErrorHandler(
    null,
    ExponentialBackOff(1000L, 1.5).also {
        // 8 minutter, som er mindre enn max.poll.interval.ms på 10 minutter.
        it.maxInterval = 60_000L * 8
    }
) {
    private val log = logger()

    override fun handleRemaining(
        thrownException: Exception,
        records: MutableList<ConsumerRecord<*, *>>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer
    ) {
        if (records.isEmpty()) {
            log.error("Feil i listener uten noen records", thrownException)
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
        invokeListener: Runnable
    ) {
        data.forEach { record ->
            loggFeilMedMaskerFnr(record, thrownException)
        }
        if (data.isEmpty()) {
            log.error("Feil i listener uten noen records", thrownException)
        }
        super.handleBatch(thrownException, data, consumer, container, invokeListener)
    }

    private fun loggFeilMedMaskerFnr(
        record: ConsumerRecord<*, *>,
        thrownException: Exception
    ) {
        val key = if (record.key().erFnr()) {
            "***"
        } else {
            record.key()
        }
    }

    private fun Any.erFnr() = this.toString().length == 11
}
