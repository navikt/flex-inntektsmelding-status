package no.nav.helse.flex.inntektsmelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.Hendelse.INNTEKTSMELDING_MANGLER
import no.nav.helse.flex.inntektsmelding.Hendelse.INNTEKTSMELDING_MOTTATT
import no.nav.helse.flex.inntektsmelding.Hendelse.VEDTAKSPERIODE_FORKASTET
import no.nav.helse.flex.kafka.bomloInntektsmeldingManglerTopic
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
@Profile("bomlo")
class InntektsmeldingConsumer(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val lockRepository: LockRepository,
) {

    val log = logger()

    @KafkaListener(
        topics = [bomloInntektsmeldingManglerTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "flex-inntektsmelding-status-inntektsmelding",
        idIsGroup = false,
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        prosesserKafkaMelding(cr.value(), cr.timestamp())

        acknowledgment.acknowledge()
    }

    @Transactional
    fun prosesserKafkaMelding(
        value: String,
        timestamp: Long,
    ) {
        val kafkaDto: InntektsmeldingKafkaDto = objectMapper.readValue(value)

        log.info("Hendelse ${kafkaDto.hendelse} for ${kafkaDto.uuid}")

        lockRepository.settAdvisoryTransactionLock(kafkaDto.fnr)

        var dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(kafkaDto.uuid.toString())?.id

        if (dbId == null) {
            dbId = inntektsmeldingRepository.save(
                InntektsmeldingDbRecord(
                    fnr = kafkaDto.fnr.toString(),
                    orgNr = kafkaDto.orgnummer,
                    orgNavn = "", // TODO: Hent orgnavn
                    opprettet = Instant.now(),
                    vedtakFom = kafkaDto.vedtaksperiodeFom,
                    vedtakTom = kafkaDto.vedtaksperiodeTom,
                    eksternId = kafkaDto.uuid.toString(),
                    eksternTimestamp = Instant.ofEpochMilli(timestamp)
                )
            ).id!!
        }

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.hendelse.tilStatus()
            )
        )
    }

    private fun Hendelse.tilStatus(): StatusVerdi {
        return when (this) {
            INNTEKTSMELDING_MANGLER -> StatusVerdi.MANGLER
            INNTEKTSMELDING_MOTTATT, VEDTAKSPERIODE_FORKASTET -> StatusVerdi.MOTTATT
        }
    }
}
