package no.nav.helse.flex.melding

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import no.nav.helse.flex.kafka.DITT_SYKEFRAVAER_MELDING_TOPIC
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.stereotype.Component

@Component
class MeldingKafkaProducer(
    private val meldingProducer: Producer<String, String>,
    private val registry: MeterRegistry,
) {
    fun produserMelding(
        meldingUuid: String,
        meldingKafkaDto: MeldingKafkaDto,
    ): RecordMetadata {
        meldingKafkaDto.opprettMelding?.let {
            registry.counter("ditt_sykefravaer_melding_sendt", Tags.of("melding_Type", it.meldingType)).increment()
        }
        meldingKafkaDto.lukkMelding?.let {
            registry.counter("ditt_sykefravaer_lukk_melding_sendt").increment()
        }
        return meldingProducer.send(
            ProducerRecord(
                DITT_SYKEFRAVAER_MELDING_TOPIC,
                meldingUuid,
                meldingKafkaDto.serialisertTilString(),
            ),
        ).get()
    }
}
