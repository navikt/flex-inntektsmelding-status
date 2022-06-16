package no.nav.helse.flex.cronjob

import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.helse.flex.brukernotifikasjon.BrukernotifikasjonKafkaProducer
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
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
class BestillBeskjed(
    private val statusRepository: StatusRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val lockRepository: LockRepository,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
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
        var beskjederBestillt = 0

        val manglerBeskjed = statusRepository
            .hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .filter { it.opprettet.isBefore(opprettetFor) }

        manglerBeskjed.forEach {
            opprettVarsler(it)
            beskjederBestillt++
        }

        if (beskjederBestillt > 0) {
            log.info("Behandlet $beskjederBestillt antall inntektsmeldinger som mangler etter 4 måneder")
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
        }

        if (medAlleStatuser.statusHistorikk.any { it.status != StatusVerdi.MANGLER_INNTEKTSMELDING }) {
            log.warn("Inntektsmelding med ekstern id ${medAlleStatuser.eksternId} kan ikke bestille beskjed med disse statusene ${medAlleStatuser.statusHistorikk.map { it.status }}")
        }

        bestillBeskjed(inntektsmeldingMedStatus, now)

        bestillMelding(inntektsmeldingMedStatus, now)
    }

    private fun bestillBeskjed(
        inntektsmeldingMedStatus: InntektsmeldingMedStatus,
        now: LocalDateTime,
    ) {
        brukernotifikasjonKafkaProducer.opprettBrukernotifikasjonBeskjed(
            NokkelInputBuilder()
                .withAppnavn("flex-inntektsmelding-status")
                .withNamespace("flex")
                .withFodselsnummer(inntektsmeldingMedStatus.fnr)
                .withEventId(inntektsmeldingMedStatus.eksternId)
                .withGrupperingsId(inntektsmeldingMedStatus.eksternId)
                .build(),
            BeskjedInputBuilder()
                .withTidspunkt(now)
                .withTekst("Vi mangler inntektsmeldingen fra ${inntektsmeldingMedStatus.orgNavn} for sykefravær f.o.m. ${inntektsmeldingMedStatus.vedtakFom.format(norskDateFormat)}. Se mer informasjon.")
                .withLink(URL(inntektsmeldingManglerUrl))
                .withSikkerhetsnivaa(4)
                .withEksternVarsling(false)
                .build()
        )

        inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = inntektsmeldingMedStatus.id,
                opprettet = now.tilOsloInstant(),
                status = StatusVerdi.BRUKERNOTIFIKSJON_SENDT,
            )
        )

        log.info("Bestillte beskjed for manglende inntektsmelding ${inntektsmeldingMedStatus.eksternId}")
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
