package no.nav.helse.flex.cronjob

import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.InntektsmeldingMedStatus
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusRepository
import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.inntektsmelding.manglendeInntektsmeldingOverlapperBehandlesUtaforSpleis
import no.nav.helse.flex.inntektsmelding.overlapper
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.erRettFør
import no.nav.helse.flex.util.finnSykefraværStart
import no.nav.helse.flex.util.norskDateFormat
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
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

    @Scheduled(initialDelay = 2, fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    fun job() {
        jobMedParameter(opprettetFor = sykmeldtVarsel())
    }

    fun jobMedParameter(opprettetFor: Instant) {
        var beskjederBestilt = 0

        val manglerBeskjed = statusRepository
            .hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .filter { it.statusOpprettet.isBefore(opprettetFor) }
            .sortedByDescending { it.vedtakFom }
            .take(100)

        manglerBeskjed.forEach {
            if (opprettVarsler(it)) {
                beskjederBestilt++
            }
        }

        if (beskjederBestilt > 0) {
            log.info("Behandlet $beskjederBestilt antall inntektsmeldinger som mangler etter 4 måneder")
        }
    }

    @Transactional
    fun opprettVarsler(inntektsmeldingMedStatus: InntektsmeldingMedStatus): Boolean {
        lockRepository.settAdvisoryTransactionLock(inntektsmeldingMedStatus.fnr.toLong())

        val vedtaksperioder = statusRepository.hentAlleForPerson(
            fnr = inntektsmeldingMedStatus.fnr,
            orgNr = inntektsmeldingMedStatus.orgNr
        )
        if (vedtaksperioder.overlapper()) {
            log.warn("Fant overlappende perioder for id ${inntektsmeldingMedStatus.id}. Vet ikke hva jeg skal gjøre. Hopper over denne ")
            return false
        }
        if (vedtaksperioder.manglendeInntektsmeldingOverlapperBehandlesUtaforSpleis()) {
            log.warn("Fant overlappende perioder med mangler im og behandles utafor spleis for id ${inntektsmeldingMedStatus.id}. Vet ikke hva jeg skal gjøre. Hopper over denne")
            return false
        }

        val harManglendeImPeriodeRettForan = vedtaksperioder
            .filter { it.status == StatusVerdi.MANGLER_INNTEKTSMELDING }
            .any { it.vedtakTom.erRettFør(inntektsmeldingMedStatus.vedtakFom) }

        if (harManglendeImPeriodeRettForan) {
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.HAR_PERIODE_RETT_FOER,
                )
            )
            return false
        }

        val medAlleStatuser = statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingMedStatus.id)!!

        if (medAlleStatuser.statusHistorikk.none { it.status == StatusVerdi.MANGLER_INNTEKTSMELDING }) {
            log.warn("Inntektsmelding med ekstern id ${medAlleStatuser.eksternId} har ikke status MANGLER_INNTEKTSMELDING, dette skal ikke skje")
            return false
        }

        if (medAlleStatuser.statusHistorikk.size > 1) {
            log.warn("Inntektsmelding med ekstern id ${medAlleStatuser.eksternId} kan ikke bestille beskjed med disse statusene ${medAlleStatuser.statusHistorikk}")
            return false
        }

        val fom = vedtaksperioder.finnSykefraværStart(inntektsmeldingMedStatus.vedtakFom)

        bestillBeskjed(inntektsmeldingMedStatus, fom)

        bestillMelding(inntektsmeldingMedStatus, fom)
        return true
    }

    private fun bestillBeskjed(inntektsmeldingMedStatus: InntektsmeldingMedStatus, fom: LocalDate) {
        val bestillingId = inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = Instant.now(),
                status = StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
            )
        ).id!!

        brukernotifikasjon.beskjedManglerInntektsmelding(
            fnr = inntektsmeldingMedStatus.fnr,
            eksternId = inntektsmeldingMedStatus.eksternId,
            bestillingId = bestillingId,
            orgNavn = inntektsmeldingMedStatus.orgNavn,
            fom = fom,
            synligFremTil = synligFremTil(),
        )
    }

    private fun synligFremTil(): Instant {
        if (env.isProduction()) {
            return OffsetDateTime.now().plusMonths(4).toInstant()
        }
        return OffsetDateTime.now().plusMinutes(20).toInstant()
    }

    private fun bestillMelding(inntektsmeldingMedStatus: InntektsmeldingMedStatus, fom: LocalDate) {
        val bestillingId = inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = Instant.now(),
                status = StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )
        ).id!!

        meldingKafkaProducer.produserMelding(
            meldingUuid = bestillingId,
            meldingKafkaDto = MeldingKafkaDto(
                fnr = inntektsmeldingMedStatus.fnr,
                opprettMelding = OpprettMelding(
                    tekst = "Vi mangler inntektsmeldingen fra ${inntektsmeldingMedStatus.orgNavn} for sykefraværet som startet ${
                    fom.format(
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

        log.info("Bestilte melding for manglende inntektsmelding ${inntektsmeldingMedStatus.eksternId}")
    }
}
