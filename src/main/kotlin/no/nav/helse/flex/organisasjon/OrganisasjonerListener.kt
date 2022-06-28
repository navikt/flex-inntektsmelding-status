package no.nav.helse.flex.organisasjon

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OrganisasjonerListener(
    private val organisasjonRepository: OrganisasjonRepository
) {
    private val log = logger()

    @KafkaListener(
        topics = ["flex.organisasjoner"],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "sykepengesoknad-organisasjon-komplett",
        idIsGroup = false,
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val value = objectMapper.readValue<NavnOgSoknad>(cr.value())

        val orgnummer = cr.key()
        val orgnavn = value.arbeidsgiver_navn
        val soknadId = value.sykepengesoknad_uuid

        val eksisterende = organisasjonRepository.findByOrgnummer(orgnummer)

        if (eksisterende == null) {
            log.info("Lagrer ny organisasjon med orgnummer: $orgnummer og navn: $orgnavn.")
            organisasjonRepository.save(
                Organisasjon(
                    orgnummer = orgnummer,
                    navn = orgnavn,
                    oppdatert = Instant.now(),
                    opprettet = Instant.now(),
                    oppdatertAv = soknadId,
                )
            )
        } else {
            if (eksisterende.navn != orgnavn) {
                log.warn("En eksisterende organisasjon $orgnummer er lagret med navnet ${eksisterende.navn} som ikke stemmer med siste navn hentet i fra sykepengesoknad-backend $orgnavn oppdatert av $soknadId")
            }
        }

        acknowledgment.acknowledge()
    }

    data class NavnOgSoknad(
        val arbeidsgiver_navn: String,
        val sykepengesoknad_uuid: String,
    )
}
