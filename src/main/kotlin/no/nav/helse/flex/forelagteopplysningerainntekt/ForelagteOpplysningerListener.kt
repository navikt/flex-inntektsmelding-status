package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@Profile("forelagteopplysninger")
class ForelagteOpplysningerListener(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
) {
    val log = logger()

    @KafkaListener(
        topics = [FORELAGTE_OPPLYSNINGER_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val valueDeserialisert: ForelagteOpplysningerMelding = objectMapper.readValue(cr.value())

        if (forelagteOpplysningerRepository.existsByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = valueDeserialisert.vedtaksperiodeId,
                behandlingId = valueDeserialisert.behandlingId,
            )
        ) {
            log.info(
                "Forelagte opplysninger for vedtaksperiode (${valueDeserialisert.vedtaksperiodeId}) " +
                    "og behandlingsid (${valueDeserialisert.behandlingId}) finnes allerede.",
            )
        } else {
            log.info(
                "Lagret forelagte opplysninger melding for vedtaksperiode (${valueDeserialisert.vedtaksperiodeId}) " +
                    "og behandlingsid (${valueDeserialisert.behandlingId})",
            )
            forelagteOpplysningerRepository.save(
                ForelagteOpplysningerDbRecord(
                    id = null,
                    fnr = null,
                    vedtaksperiodeId = valueDeserialisert.vedtaksperiodeId,
                    behandlingId = valueDeserialisert.behandlingId,
                    forelagteOpplysningerMelding =
                        PGobject().also {
                            it.type = "json"
                            it.value = cr.value()
                        },
                    opprettet = Instant.now(),
                    forelagt = null,
                ),
            )
        }
        acknowledgment.acknowledge()
    }
}

const val FORELAGTE_OPPLYSNINGER_TOPIC = "tbd.forelagte-opplysninger"
