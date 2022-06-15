package no.nav.helse.flex.inntektsmelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.kafka.bomloInntektsmeldingManglerTopic
import no.nav.helse.flex.kafka.inntektsmeldingstatusTestdataTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class InntektsmeldingConsumer(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val lockRepository: LockRepository,
) {

    val log = logger()

    @KafkaListener(
        topics = [inntektsmeldingstatusTestdataTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "flex-inntektsmelding-status-inntektsmelding-testdata",
        idIsGroup = false,
    )
    fun listenToTest(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        prosesserKafkaMelding(cr.value())

        acknowledgment.acknowledge()
    }

    @Profile("bomlo")
    @KafkaListener(
        topics = [bomloInntektsmeldingManglerTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "flex-inntektsmelding-status-inntektsmelding",
        idIsGroup = false,
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        prosesserKafkaMelding(cr.value())

        acknowledgment.acknowledge()
    }

    @Transactional
    fun prosesserKafkaMelding(value: String) {
        val kafkaDto: InntektsmeldingKafkaDto = objectMapper.readValue(value)

        log.info("Hendelse ${kafkaDto.status} for ${kafkaDto.id}")

        // TODO: test med lock
        // lockRepository.settAdvisoryTransactionLock(kafkaDto.fnr)

        var dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(kafkaDto.id)?.id

        if (dbId == null) {
            dbId = inntektsmeldingRepository.save(
                InntektsmeldingDbRecord(
                    fnr = kafkaDto.sykmeldt,
                    orgNr = kafkaDto.arbeidsgiver,
                    orgNavn = "", // TODO: Hent orgnavn
                    opprettet = Instant.now(),
                    vedtakFom = kafkaDto.vedtaksperiode.fom,
                    vedtakTom = kafkaDto.vedtaksperiode.tom,
                    eksternId = kafkaDto.id,
                    eksternTimestamp = kafkaDto.tidspunkt.toInstant()
                )
            ).id!!
        }

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )
    }

    private fun Status.tilStatusVerdi(): StatusVerdi {
        return when (this) {
            Status.MANGLER_INNTEKTSMELDING -> StatusVerdi.MANGLER_INNTEKTSMELDING
            Status.HAR_INNTEKTSMELDING -> StatusVerdi.HAR_INNTEKTSMELDING
            Status.TRENGER_IKKE_INNTEKTSMELDING -> StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING
            Status.BEHANDLES_UTENFOR_SPLEIS -> StatusVerdi.BEHANDLES_UTENFOR_SPLEIS
        }
    }
}
