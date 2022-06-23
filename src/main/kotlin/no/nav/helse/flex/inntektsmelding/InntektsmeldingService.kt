package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.LukkMelding
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class InntektsmeldingService(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val statusRepository: StatusRepository,
    private val brukernotifikasjon: Brukernotifikasjon,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val organisasjonRepository: OrganisasjonRepository,
    private val lockRepository: LockRepository,
) {
    val log = logger()

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
                    orgNavn = organisasjonRepository.findByOrgnummer(kafkaDto.arbeidsgiver)!!.navn,
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
        inntektsmelding: InntektsmeldingMedStatusHistorikk
    ) {
        if (inntektsmelding.statusHistorikk.isNotEmpty()) {
            if (inntektsmelding.statusHistorikk.size == 1 && inntektsmelding.statusHistorikk.first() == StatusVerdi.MANGLER_INNTEKTSMELDING) {
                log.info("Inntektsmelding ${inntektsmelding.eksternId} har allerede status for MANGLER_INNTEKTSMELDING, lagrer ikke dublikat")
                return
            }

            throw RuntimeException("Inntektsmelding ${inntektsmelding.eksternId} med status MANGLER_INNTEKTSMELDING har allerede disse statusene ${inntektsmelding.statusHistorikk}")
        }

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        log.info("Inntektsmelding ${inntektsmelding.eksternId} mangler inntektsmelding")
    }

    private fun harInntektsmelding(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        inntektsmelding: InntektsmeldingMedStatusHistorikk
    ) {
        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        if (inntektsmelding.statusHistorikk.isEmpty()) {
            log.info("Inntektsmelding ${inntektsmelding.eksternId} har inntektsmelding, gjør ingenting")
            return
        }

        log.info("Inntektsmelding ${inntektsmelding.eksternId} har mottatt manglende inntektsmelding")

        if (StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT in inntektsmelding.statusHistorikk) {
            doneBeskjed(inntektsmelding, dbId)
        }

        if (StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT in inntektsmelding.statusHistorikk) {
            doneMelding(inntektsmelding, dbId)

            // TODO: Send inntektsmelding mottatt melding
        }
    }

    private fun trengerIkkeInntektsmelding(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        inntektsmelding: InntektsmeldingMedStatusHistorikk
    ) {
        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        log.info("Inntektsmelding ${inntektsmelding.eksternId} trenger ikke inntektsmelding")

        if (StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT in inntektsmelding.statusHistorikk) {
            doneBeskjed(inntektsmelding, dbId)
        }

        if (StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT in inntektsmelding.statusHistorikk) {
            doneMelding(inntektsmelding, dbId)
        }
    }

    private fun behandlesUtenforSpleis(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        inntektsmelding: InntektsmeldingMedStatusHistorikk
    ) {
        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        log.info("Inntektsmelding ${inntektsmelding.eksternId} behandles utenfor spleis")

        if (StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT in inntektsmelding.statusHistorikk) {
            doneBeskjed(inntektsmelding, dbId)
        }

        if (StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT in inntektsmelding.statusHistorikk) {
            doneMelding(inntektsmelding, dbId)
        }
    }

    private fun doneBeskjed(
        inntektsmelding: InntektsmeldingMedStatusHistorikk,
        dbId: String,
    ) {
        if (StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT in inntektsmelding.statusHistorikk) {
            log.info("Inntektsmelding ${inntektsmelding.eksternId} har allerede donet brukernotifikasjon beskjed")
            return
        }

        brukernotifikasjon.sendDonemelding(
            fnr = inntektsmelding.fnr,
            eksternId = inntektsmelding.eksternId,
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
        inntektsmelding: InntektsmeldingMedStatusHistorikk,
        dbId: String,
    ) {
        if (StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT in inntektsmelding.statusHistorikk) {
            log.info("Inntektsmelding ${inntektsmelding.eksternId} har allerede donet ditt sykefravær melding")
            return
        }

        meldingKafkaProducer.produserMelding(
            meldingUuid = inntektsmelding.eksternId,
            meldingKafkaDto = MeldingKafkaDto(
                fnr = inntektsmelding.fnr,
                lukkMelding = LukkMelding(
                    timestamp = Instant.now(),
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
