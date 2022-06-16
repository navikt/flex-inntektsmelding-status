package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class InntekstmeldingService(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val lockRepository: LockRepository,
) {
    val log = logger()

    @Transactional
    fun prosesserKafkaMelding(kafkaDto: InntektsmeldingKafkaDto) {
        val eksternId = kafkaDto.vedtaksperiode.id

        log.info("Inntektsmelding status ${kafkaDto.status} for $eksternId")

        // TODO: test med lock
        // lockRepository.settAdvisoryTransactionLock(kafkaDto.fnr)

        var dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)?.id

        if (dbId == null) {
            dbId = inntektsmeldingRepository.save(
                InntektsmeldingDbRecord(
                    fnr = kafkaDto.sykmeldt,
                    orgNr = kafkaDto.arbeidsgiver,
                    orgNavn = "", // TODO: Hent orgnavn
                    opprettet = Instant.now(),
                    vedtakFom = kafkaDto.vedtaksperiode.fom,
                    vedtakTom = kafkaDto.vedtaksperiode.tom,
                    eksternId = eksternId,
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
            Status.MANGLER_INNTEKTSMELDING -> StatusVerdi.MANGLER_INNTEKTSMELDING // Venter på inntektsmelding
            Status.HAR_INNTEKTSMELDING -> StatusVerdi.HAR_INNTEKTSMELDING // Inntektsmelding mottatt, eller vedtaksperiode som ikke trenger ny inntektsmelding
            Status.TRENGER_IKKE_INNTEKTSMELDING -> StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING // Ikke utbetaling, innenfor arbeidsgiverperiode
            Status.BEHANDLES_UTENFOR_SPLEIS -> StatusVerdi.BEHANDLES_UTENFOR_SPLEIS // Kastes ut fra speil og behandles i gosys
        }
    }
}
