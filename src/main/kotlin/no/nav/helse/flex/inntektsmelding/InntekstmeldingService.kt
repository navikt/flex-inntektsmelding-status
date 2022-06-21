package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.LukkMelding
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.util.tilOsloInstant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import javax.annotation.PostConstruct

@Component
class InntekstmeldingService(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val statusRepository: StatusRepository,
    private val brukernotifikasjon: Brukernotifikasjon,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val lockRepository: LockRepository,
) {
    val log = logger()

    @PostConstruct
    fun cleanDB() {
        inntektsmeldingStatusRepository.deleteAll()
        inntektsmeldingRepository.deleteAll()
    }

    @Transactional
    fun prosesserKafkaMelding(kafkaDto: InntektsmeldingKafkaDto) {
        val eksternId = kafkaDto.vedtaksperiode.id

        // TODO: test med lock
        // lockRepository.settAdvisoryTransactionLock(kafkaDto.fnr)

        val dbId = lagreInntektsmeldingHvisDenIkkeFinnesAllerede(kafkaDto, eksternId)
        val inntektsmeldingMedStatusHistorikk = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!

        when (kafkaDto.status) {
            Status.MANGLER_INNTEKTSMELDING -> manglerInntektsmelding(kafkaDto, dbId, inntektsmeldingMedStatusHistorikk)
            Status.HAR_INNTEKTSMELDING -> harInntektsmelding(kafkaDto, dbId, inntektsmeldingMedStatusHistorikk)
            Status.TRENGER_IKKE_INNTEKTSMELDING -> trengerIkkeInntektsmelding(
                kafkaDto,
                dbId,
                inntektsmeldingMedStatusHistorikk
            )
            Status.BEHANDLES_UTENFOR_SPLEIS -> behandlesUtenforSpleis(kafkaDto, dbId, inntektsmeldingMedStatusHistorikk)
        }
    }

    private fun lagreInntektsmeldingHvisDenIkkeFinnesAllerede(
        kafkaDto: InntektsmeldingKafkaDto,
        eksternId: String
    ): String {
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

            log.info("Lagret ny inntektsmelding periode $eksternId")
        }

        return dbId
    }

    private fun manglerInntektsmelding(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        statusHistorikk: InntektsmeldingMedStatusHistorikk
    ) {
        if (statusHistorikk.statusHistorikk.isNotEmpty()) {
            if (statusHistorikk.statusHistorikk.size == 1 && statusHistorikk.statusHistorikk.first().status == StatusVerdi.MANGLER_INNTEKTSMELDING) {
                log.info("Inntektsmelding ${statusHistorikk.eksternId} har allerede status for MANGLER_INNTEKTSMELDING, lagrer ikke dublikat")
                return
            }

            throw RuntimeException("Inntektsmelding ${statusHistorikk.eksternId} med status MANGLER_INNTEKTSMELDING har allerede disse statusene ${statusHistorikk.statusHistorikk.map { it.status }}")
        }

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        log.info("Inntektsmelding ${statusHistorikk.eksternId} mangler inntektsmelding")
    }

    private fun harInntektsmelding(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        statusHistorikk: InntektsmeldingMedStatusHistorikk
    ) {
        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        if (statusHistorikk.statusHistorikk.isEmpty()) {
            log.info("Inntektsmelding ${statusHistorikk.eksternId} har inntektsmelding, gjør ingenting")
            return
        }

        log.info("Inntektsmelding ${statusHistorikk.eksternId} har mottatt manglende inntektsmelding")

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }) {
            doneBeskjed(statusHistorikk, dbId)

            // TODO: Send inntektsmelding mottatt beskjed
        }

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }) {
            doneMelding(statusHistorikk, dbId)

            // TODO: Send inntektsmelding mottatt melding
        }
    }

    private fun trengerIkkeInntektsmelding(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        statusHistorikk: InntektsmeldingMedStatusHistorikk
    ) {
        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        log.info("Inntektsmelding ${statusHistorikk.eksternId} trenger ikke inntektsmelding")

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }) {
            doneBeskjed(statusHistorikk, dbId)
        }

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }) {
            doneMelding(statusHistorikk, dbId)
        }
    }

    private fun behandlesUtenforSpleis(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        statusHistorikk: InntektsmeldingMedStatusHistorikk
    ) {
        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        log.info("Inntektsmelding ${statusHistorikk.eksternId} behandles utenfor spleis")

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }) {
            doneBeskjed(statusHistorikk, dbId)
        }

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }) {
            doneMelding(statusHistorikk, dbId)
        }
    }

    private fun doneBeskjed(
        statusHistorikk: InntektsmeldingMedStatusHistorikk,
        dbId: String,
    ) {
        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT }) {
            log.info("Inntektsmelding ${statusHistorikk.eksternId} har allerede donet brukernotifikasjon beskjed")
            return
        }

        brukernotifikasjon.sendDonemelding(
            fnr = statusHistorikk.fnr,
            eksternId = statusHistorikk.eksternId,
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT
            )
        )
    }

    private fun doneMelding(
        statusHistorikk: InntektsmeldingMedStatusHistorikk,
        dbId: String,
    ) {
        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT }) {
            log.info("Inntektsmelding ${statusHistorikk.eksternId} har allerede donet ditt sykefravær melding")
            return
        }

        meldingKafkaProducer.produserMelding(
            meldingUuid = statusHistorikk.eksternId,
            meldingKafkaDto = MeldingKafkaDto(
                fnr = statusHistorikk.fnr,
                lukkMelding = LukkMelding(
                    timestamp = LocalDateTime.now().tilOsloInstant(),
                ),
            )
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT
            )
        )
    }
}
