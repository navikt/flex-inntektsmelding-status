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
            Status.BEHANDLES_UTENFOR_SPLEIS -> behandlesUtenforSplies(kafkaDto, dbId, inntektsmeldingMedStatusHistorikk)
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
                log.info("Inntektsmelding ${kafkaDto.vedtaksperiode.id} har allerede status for MANGLER_INNTEKTSMELDING, lagrer ikke dublikat")
                return
            }

            throw RuntimeException("Inntektsmelding ${kafkaDto.vedtaksperiode.id} med status MANGLER_INNTEKTSMELDING har allerede disse statusene ${statusHistorikk.statusHistorikk.map { it.status }}")
        }

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = kafkaDto.status.tilStatusVerdi()
            )
        )

        log.info("Inntektsmelding ${kafkaDto.vedtaksperiode.id} er lagret med status ${kafkaDto.status.tilStatusVerdi()}")
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
            log.info("Inntektsmelding ${kafkaDto.vedtaksperiode.id} har inntektsmelding, gjør ingenting")
            return
        }

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_SENDT }) {
            if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_DONE_SENDT }) {
                log.info("Inntektsmelding ${kafkaDto.vedtaksperiode.id} har mottatt inntektsmelding og brukernotifikasjon er allerede donet")
            } else {
                brukernotifikasjon.sendDonemelding(
                    fnr = statusHistorikk.fnr,
                    eksternId = statusHistorikk.eksternId,
                )

                inntektsmeldingStatusRepository.save(
                    InntektsmeldingStatusDbRecord(
                        inntektsmeldingId = dbId,
                        opprettet = Instant.now(),
                        status = StatusVerdi.BRUKERNOTIFIKSJON_DONE_SENDT
                    )
                )
            }

            // TODO: Send inntektsmelding mottatt beskjed
        }

        if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_MELDING_SENDT }) {
            // TODO: Hvordan blir flyten når meldingen lukkes i fra ditt sykefravær, må vi også sjekke DITT_SYKEFRAVAER_LUKKET
            if (statusHistorikk.statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_DONE_SENDT }) {
                log.info("Inntektsmelding ${kafkaDto.vedtaksperiode.id} har mottatt inntektsmelding og ditt sykefravær melding er allerede donet")
            } else {
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
                        status = StatusVerdi.DITT_SYKEFRAVAER_DONE_SENDT
                    )
                )
            }

            // TODO: Send inntektsmelding mottatt melding
        }
    }

    private fun trengerIkkeInntektsmelding(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        statusHistorikk: InntektsmeldingMedStatusHistorikk
    ) {
        log.info("trengerIkkeInntektsmelding $kafkaDto $dbId $statusHistorikk")
        // if brukernot beskjed, send done
        // if dittsykefravær melding, send lukkmelding
        // if første status, lagre og ok
    }

    private fun behandlesUtenforSplies(
        kafkaDto: InntektsmeldingKafkaDto,
        dbId: String,
        statusHistorikk: InntektsmeldingMedStatusHistorikk
    ) {
        log.info("behandlesUtenforSplies $kafkaDto $dbId $statusHistorikk")
        // if ikke første status, kast feil
        // else lagre og ok
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
