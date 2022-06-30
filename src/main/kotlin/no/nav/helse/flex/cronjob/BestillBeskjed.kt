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
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.norskDateFormat
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class BestillBeskjed(
    private val statusRepository: StatusRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val lockRepository: LockRepository,
    private val brukernotifikasjon: Brukernotifikasjon,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val env: EnvironmentToggles,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
    @Value("\${INNTEKTSMELDING_MANGLER_VENTETID}") private val ventetid: Long,
) {

    private val log = logger()

    private fun sykmeldtVarsel() = OffsetDateTime.now().minusDays(ventetid).toInstant()

    @Scheduled(initialDelay = 2, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun job() {
        if (env.isProduction()) {
            log.info("Bestiller ikke beskjed og melding i prod")
            return
        }

        jobMedParameter(opprettetFor = sykmeldtVarsel())
    }

    fun jobMedParameter(opprettetFor: Instant) {
        var beskjederBestilt = 0

        val manglerBeskjed = statusRepository
            .hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .filter { it.statusOpprettet.isBefore(opprettetFor) }

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

        if (StatusVerdi.MANGLER_INNTEKTSMELDING !in medAlleStatuser.statusHistorikk) {
            log.warn("Inntektsmelding med ekstern id ${medAlleStatuser.eksternId} har ikke status MANGLER_INNTEKTSMELDING, dette skal ikke skje")
            return
        }

        if (medAlleStatuser.statusHistorikk.size > 1) {
            log.warn("Inntektsmelding med ekstern id ${medAlleStatuser.eksternId} kan ikke bestille beskjed med disse statusene ${medAlleStatuser.statusHistorikk}")
            return
        }

        bestillBeskjed(inntektsmeldingMedStatus)

        bestillMelding(inntektsmeldingMedStatus)
    }

    private fun bestillBeskjed(inntektsmeldingMedStatus: InntektsmeldingMedStatus) {
        brukernotifikasjon.beskjedManglerInntektsmelding(
            fnr = inntektsmeldingMedStatus.fnr,
            eksternId = inntektsmeldingMedStatus.eksternId,
            orgNavn = inntektsmeldingMedStatus.orgNavn,
            fom = inntektsmeldingMedStatus.vedtakFom,
            synligFremTil = synligFremTil(),
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = Instant.now(),
                status = StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
            )
        )
    }

    private fun synligFremTil(): Instant {
        if (env.isProduction()) {
            return OffsetDateTime.now().plusMonths(4).toInstant()
        }
        return OffsetDateTime.now().plusMinutes(20).toInstant()
    }

    private fun bestillMelding(inntektsmeldingMedStatus: InntektsmeldingMedStatus) {
        meldingKafkaProducer.produserMelding(
            meldingUuid = inntektsmeldingMedStatus.eksternId,
            meldingKafkaDto = MeldingKafkaDto(
                fnr = inntektsmeldingMedStatus.fnr,
                opprettMelding = OpprettMelding(
                    tekst = "Vi mangler inntektsmeldingen fra ${inntektsmeldingMedStatus.orgNavn} for sykefravær f.o.m. ${
                    inntektsmeldingMedStatus.vedtakFom.format(
                        norskDateFormat
                    )
                    }.",
                    lenke = inntektsmeldingManglerUrl,
                    variant = Variant.info,
                    lukkbar = false,
                    synligFremTil = synligFremTil(),
                    meldingType = "MANGLENDE_INNTEKTSMELDING",
                ),
            )
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = Instant.now(),
                status = StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )
        )

        log.info("Bestilte melding for manglende inntektsmelding ${inntektsmeldingMedStatus.eksternId}")
    }
}
