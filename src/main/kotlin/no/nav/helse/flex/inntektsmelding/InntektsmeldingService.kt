package no.nav.helse.flex.inntektsmelding

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.LukkMelding
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.util.norskDateFormat
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
    private val registry: MeterRegistry

) {
    val log = logger()

    @Transactional
    fun prosesserKafkaMelding(kafkaDto: InntektsmeldingKafkaDto) {
        registry.counter("inntektsmelding_status_mottatt", Tags.of("status", kafkaDto.status.toString())).increment()

        val eksternId = kafkaDto.vedtaksperiode.id

        lockRepository.settAdvisoryTransactionLock(kafkaDto.sykmeldt.toLong())

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
                    orgNavn = organisasjonRepository.findByOrgnummer(kafkaDto.arbeidsgiver)?.navn
                        ?: throw RuntimeException("Finner ikke orgnummer ${kafkaDto.arbeidsgiver}"),
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
            if (inntektsmelding.statusHistorikk.size == 1 && inntektsmelding.statusHistorikk.first().status == StatusVerdi.MANGLER_INNTEKTSMELDING) {
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

        if (inntektsmelding.manglerBeskjedSendt()) {
            doneBeskjed(inntektsmelding, dbId)
        }

        if (inntektsmelding.manglerMeldingSendt()) {
            doneMelding(inntektsmelding, dbId)
            bestillMeldingMottattInntektsmelding(inntektsmelding)
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

        if (inntektsmelding.manglerBeskjedSendt()) {
            doneBeskjed(inntektsmelding, dbId)
        }

        if (inntektsmelding.manglerMeldingSendt()) {
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

        if (inntektsmelding.manglerBeskjedSendt()) {
            doneBeskjed(inntektsmelding, dbId)
        }

        if (inntektsmelding.manglerMeldingSendt()) {
            doneMelding(inntektsmelding, dbId)
        }
    }

    private fun doneBeskjed(
        inntektsmelding: InntektsmeldingMedStatusHistorikk,
        dbId: String,
    ) {
        if (inntektsmelding.manglerBeskjedDonet()) {
            log.info("Inntektsmelding ${inntektsmelding.eksternId} har allerede donet brukernotifikasjon beskjed")
            return
        }

        val bestillingId =
            inntektsmelding.statusHistorikk.first { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }.id

        brukernotifikasjon.sendDonemelding(
            fnr = inntektsmelding.fnr,
            eksternId = inntektsmelding.eksternId,
            bestillingId = bestillingId,
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT
            )
        )

        log.info("Donet brukernotifikasjon beskjed om manglende inntektsmelding ${inntektsmelding.eksternId}")
    }

    private fun doneMelding(
        inntektsmelding: InntektsmeldingMedStatusHistorikk,
        dbId: String,
    ) {
        if (inntektsmelding.manglerMeldingDonet()) {
            log.info("Inntektsmelding ${inntektsmelding.eksternId} har allerede donet ditt sykefravær melding")
            return
        }

        val bestillingId =
            inntektsmelding.statusHistorikk.first { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }.id

        meldingKafkaProducer.produserMelding(
            meldingUuid = bestillingId,
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

        log.info("Donet ditt sykefravær melding om manglende inntektsmelding ${inntektsmelding.eksternId}")
    }

    private fun bestillMeldingMottattInntektsmelding(inntektsmeldingMedStatus: InntektsmeldingMedStatusHistorikk) {
        val bestillingId = inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = Instant.now(),
                status = StatusVerdi.DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT,
            )
        ).id!!

        meldingKafkaProducer.produserMelding(
            meldingUuid = bestillingId,
            meldingKafkaDto = MeldingKafkaDto(
                fnr = inntektsmeldingMedStatus.fnr,
                opprettMelding = OpprettMelding(
                    tekst = "Vi har mottatt inntektsmeldingen fra ${inntektsmeldingMedStatus.orgNavn} for sykefraværet som startet ${
                    inntektsmeldingMedStatus.vedtakFom.format(
                        norskDateFormat
                    )
                    }.",
                    lenke = null,
                    variant = Variant.success,
                    lukkbar = true,
                    meldingType = "MOTTATT_INNTEKTSMELDING",
                ),
            )
        )

        log.info("Bestilte melding for mottatt inntektsmelding ${inntektsmeldingMedStatus.eksternId}")
    }
}
