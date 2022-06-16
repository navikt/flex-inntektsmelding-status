package no.nav.helse.flex.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.helse.flex.kafka.brukernotifikasjonBeskjedTopic
import no.nav.helse.flex.kafka.brukernotifikasjonDoneTopic
import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class BrukernotifikasjonKafkaProducer(
    private val beskjedKafkaProducer: Producer<NokkelInput, BeskjedInput>,
    private val doneKafkaProducer: Producer<NokkelInput, DoneInput>
) {
    val log = logger()

    fun opprettBrukernotifikasjonBeskjed(nokkel: NokkelInput, beskjed: BeskjedInput) {
        try {
            beskjedKafkaProducer.send(
                ProducerRecord(
                    brukernotifikasjonBeskjedTopic,
                    nokkel,
                    beskjed
                )
            ).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sending av oppgave med id ${nokkel.getEventId()}", e)
            throw e
        }
    }

    fun sendDonemelding(nokkel: NokkelInput, done: DoneInput) {
        try {
            doneKafkaProducer.send(
                ProducerRecord(
                    brukernotifikasjonDoneTopic,
                    nokkel,
                    done
                )
            ).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved ferdigstilling av oppgave med id ${nokkel.getEventId()}", e)
            throw e
        }
    }
}
