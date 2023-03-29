package no.nav.helse.flex.melding

import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.kafka.dittSykefravaerMeldingTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MeldingConsumer(
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val registry: MeterRegistry
) {

    val log = logger()

    @KafkaListener(
        topics = [dittSykefravaerMeldingTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "ditt-sykefravaer-melding",
        idIsGroup = false
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        log.info("Mottok melding om lukking av ditt-sykefravær-melding med key: ${cr.key()}")

        prosesserKafkaMelding(cr.key(), cr.value())
        acknowledgment.acknowledge()
    }

    fun prosesserKafkaMelding(
        key: String,
        value: String
    ) {
        val meldingKafkaDto: MeldingKafkaDto = objectMapper.readValue(value)

        if (meldingKafkaDto.lukkMelding == null) {
            return
        }

        val melding = inntektsmeldingStatusRepository.findByIdOrNull(key) ?: return
        val eksternId = inntektsmeldingRepository.findByIdOrNull(melding.inntektsmeldingId)!!.eksternId

        if (melding.status == StatusVerdi.DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT) {
            log.info(
                "Melding status er DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT, oppdaterer status til " +
                    "DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_LUKKET for inntektsmelding med eksternId: $eksternId"
            )
            registry.counter("ditt_sykefravaer_lukk_melding_mottatt").increment()
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = melding.inntektsmeldingId,
                    opprettet = Instant.now(),
                    status = StatusVerdi.DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_LUKKET
                )
            )

            log.info("Lukket ditt-sykefravær-melding om mottatt inntektsmelding med eksternId: $eksternId")
        }
    }
}
