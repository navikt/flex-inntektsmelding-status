package no.nav.helse.flex.organisasjon

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.sykepengesoknadTopic
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykepengesoknadListener(
    val organisasjonsOppdatering: OrganisasjonOppdatering
) {

    @KafkaListener(
        topics = [sykepengesoknadTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "sykepengesoknad-organisasjon",
        idIsGroup = false
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val soknad = cr.value().tilSykepengesoknadDTO()
        organisasjonsOppdatering.handterSoknad(soknad)
        acknowledgment.acknowledge()
    }

    fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
}
