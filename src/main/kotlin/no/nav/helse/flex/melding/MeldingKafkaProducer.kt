package no.nav.helse.flex.melding

import no.nav.helse.flex.kafka.dittSykefravaerMeldingTopic
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.stereotype.Component

@Component
class MeldingKafkaProducer(
    private val meldingProducer: KafkaProducer<String, String>
) {

    fun produserMelding(meldingUuid: String, meldingKafkaDto: MeldingKafkaDto): RecordMetadata {

        return meldingProducer.send(
            ProducerRecord(
                dittSykefravaerMeldingTopic,
                meldingUuid,
                meldingKafkaDto.serialisertTilString()
            )
        ).get()
    }
}
