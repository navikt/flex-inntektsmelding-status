package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("forelagteopplysninger")
class ForelagteOpplysningerListener(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
) {
    val log = logger()

    @KafkaListener(
        topics = [FORELAGTE_OPPLYSNINGER_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        groupId = "forelagteopplysninger-3",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val forelagtOpplysningerDbRecord = ForelagteOpplysningerDbRecord.parseConsumerRecord(cr)

        if (forelagteOpplysningerRepository.existsByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = forelagtOpplysningerDbRecord.vedtaksperiodeId,
                behandlingId = forelagtOpplysningerDbRecord.behandlingId,
            )
        ) {
            log.info(
                "Forelagte opplysninger for vedtaksperiode (${forelagtOpplysningerDbRecord.vedtaksperiodeId}) " +
                    "og behandlingsid (${forelagtOpplysningerDbRecord.behandlingId}) finnes allerede.",
            )
        } else {
            log.info(
                "Lagret forelagte opplysninger melding for vedtaksperiode (${forelagtOpplysningerDbRecord.vedtaksperiodeId}) " +
                    "og behandlingsid (${forelagtOpplysningerDbRecord.behandlingId})",
            )
            forelagteOpplysningerRepository.save(forelagtOpplysningerDbRecord)
        }
        acknowledgment.acknowledge()
    }
}

const val FORELAGTE_OPPLYSNINGER_TOPIC = "tbd.forelagte-opplysninger"
