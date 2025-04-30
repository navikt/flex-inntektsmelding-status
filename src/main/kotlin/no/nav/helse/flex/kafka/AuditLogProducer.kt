package no.nav.helse.flex.kafka

import no.nav.helse.flex.auditlogging.AuditEntry
import no.nav.helse.flex.logger
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class AuditLogProducer(
    val producer: KafkaProducer<String, String>,
) {
    val log = logger()

    fun lagAuditLog(auditEntry: AuditEntry) {
        try {
            producer
                .send(
                    ProducerRecord(
                        AUDIT_TOPIC,
                        auditEntry.serialisertTilString(),
                    ),
                ).get()
        } catch (e: Exception) {
            log.error("Klarte ikke publisere AuditEntry på kafka")
            throw AivenKafkaException(e)
        }
    }
}

class AivenKafkaException(
    e: Throwable,
) : RuntimeException(e)
