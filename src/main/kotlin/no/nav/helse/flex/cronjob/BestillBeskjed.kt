package no.nav.helse.flex.cronjob

import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.InntektsmeldingMedStatus
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusRepository
import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.util.norskDateFormat
import no.nav.helse.flex.util.tilOsloInstant
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
class BestillBeskjed(
    private val statusRepository: StatusRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val lockRepository: LockRepository,
    private val brukernotifikasjon: Brukernotifikasjon,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
) {

    private val log = logger()

    private fun arbeidsgiverVarsel() = LocalDateTime.now().minusWeeks(3)
    private fun sykmeldtVarsel() = arbeidsgiverVarsel().minusWeeks(1).tilOsloInstant()

    @Scheduled(initialDelay = 2, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun job() {
        jobMedParameter(opprettetFor = sykmeldtVarsel())
    }

    fun jobMedParameter(opprettetFor: Instant) {
        var beskjederBestilt = 0

        val manglerBeskjed = statusRepository
            .hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .filter { it.opprettet.isBefore(opprettetFor) }

        manglerBeskjed.forEach {
            opprettVarsler(it)
            beskjederBestilt++
        }

        if (beskjederBestilt > 0) {
            log.info("Behandlet $beskjederBestilt antall inntektsmeldinger som mangler etter 4 måneder")
        }
    }

    @Transactional
    fun opprettVarsler(inntektsmeldingMedStatus: InntektsmeldingMedStatus) {
        // TODO: test lock
        // lockRepository.settAdvisoryTransactionLock(inntektsmeldingMedStatus.fnr.toLong())

        val medAlleStatuser = statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingMedStatus.id)!!
        val now = LocalDateTime.now()

        if (medAlleStatuser.statusHistorikk.none { it.status == StatusVerdi.MANGLER_INNTEKTSMELDING }) {
            log.warn("Inntektsmelding med ekstern id ${medAlleStatuser.eksternId} har ikke status MANGLER_INNTEKTSMELDING, dette skal ikke skje")
            return
        }

        if (medAlleStatuser.statusHistorikk.any { it.status != StatusVerdi.MANGLER_INNTEKTSMELDING }) {
            log.warn("Inntektsmelding med ekstern id ${medAlleStatuser.eksternId} kan ikke bestille beskjed med disse statusene ${medAlleStatuser.statusHistorikk.map { it.status }}")
            return
        }

        bestillBeskjed(inntektsmeldingMedStatus, now)

        bestillMelding(inntektsmeldingMedStatus, now)
    }

    private fun bestillBeskjed(
        inntektsmeldingMedStatus: InntektsmeldingMedStatus,
        now: LocalDateTime,
    ) {
        brukernotifikasjon.beskjedManglerInntektsmelding(
            fnr = inntektsmeldingMedStatus.fnr,
            eksternId = inntektsmeldingMedStatus.eksternId,
            orgNavn = inntektsmeldingMedStatus.orgNavn,
            fom = inntektsmeldingMedStatus.vedtakFom,
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = now.tilOsloInstant(),
                status = StatusVerdi.BRUKERNOTIFIKSJON_SENDT,
            )
        )
    }

    private fun bestillMelding(
        inntektsmeldingMedStatus: InntektsmeldingMedStatus,
        now: LocalDateTime,
    ) {
        meldingKafkaProducer.produserMelding(
            meldingUuid = inntektsmeldingMedStatus.eksternId,
            meldingKafkaDto = MeldingKafkaDto(
                fnr = inntektsmeldingMedStatus.fnr,
                opprettMelding = OpprettMelding(
                    tekst = "Vi mangler inntektsmeldingen fra ${inntektsmeldingMedStatus.orgNavn} for sykefravær f.o.m. ${inntektsmeldingMedStatus.vedtakFom.format(norskDateFormat)}.",
                    lenke = inntektsmeldingManglerUrl,
                    variant = Variant.info,
                    lukkbar = false,
                    meldingType = "MANGLENDE_INNTEKTSMELDING",
                ),
            )
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = now.tilOsloInstant(),
                status = StatusVerdi.DITT_SYKEFRAVAER_MELDING_SENDT,
            )
        )

        log.info("Bestillte melding for manglende inntektsmelding ${inntektsmeldingMedStatus.eksternId}")
    }
}
