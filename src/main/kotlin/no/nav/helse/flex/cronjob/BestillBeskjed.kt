package no.nav.helse.flex.cronjob

import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.*
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
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class BestillBeskjed(
    private val statusRepository: StatusRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
    private val lockRepository: LockRepository,
    private val brukernotifikasjon: Brukernotifikasjon,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val env: EnvironmentToggles,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
) {
    private val log = logger()

    private fun sykmeldtVarsel() = OffsetDateTime.now().toInstant()

    // Oppretter transaksjon her for å sikret at advisory lock ikke relases før metoden har kjørt ferdig.
    @Transactional
    fun opprettVarsler(inntektsmeldingMedStatus: InntektsmeldingMedStatus): Boolean {
        lockRepository.settAdvisoryTransactionLock(inntektsmeldingMedStatus.fnr.toLong())

        val vedtaksperioder =
            statusRepository.hentAlleForPerson(
                fnr = inntektsmeldingMedStatus.fnr,
                orgNr = inntektsmeldingMedStatus.orgNr,
            )
        if (vedtaksperioder.overlapper()) {
            log.info("Fant overlappende perioder for id ${inntektsmeldingMedStatus.id}. Vet ikke hva jeg skal gjøre. Hopper over denne.")
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.OVELAPPER_SENDER_IKKE_UT,
                ),
            )
            return false
        }
        if (vedtaksperioder.manglendeInntektsmeldingOverlapperBehandlesUtaforSpleis()) {
            log.info(
                "Fant overlappende perioder med manglende inntektsmelding ${inntektsmeldingMedStatus.id} som behandles " +
                    "utenfor Spleis. Vet ikke hva jeg skal gjøre. Hopper over denne.",
            )
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.OVELAPPER_BEHANDLES_UTAFOR_SPLEIS_SENDER_IKKE_UT,
                ),
            )
            return false
        }

        val harManglendeImPeriodeRettForan =
            vedtaksperioder
                .filter { it.status == StatusVerdi.MANGLER_INNTEKTSMELDING }
                .any { it.vedtakTom.erRettFør(inntektsmeldingMedStatus.vedtakFom) }

        if (harManglendeImPeriodeRettForan) {
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.HAR_PERIODE_RETT_FOER,
                ),
            )
            return false
        }

        val inntektsmeldingMedStatusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingMedStatus.id)!!

        if (!inntektsmeldingMedStatusHistorikk.alleBrukernotifikasjonerErDonet()) {
            log.warn(
                "Bestiller ikke beskjed for inntektsmelding med eksternId ${inntektsmeldingMedStatusHistorikk.eksternId} og " +
                    "statuser ${inntektsmeldingMedStatusHistorikk.statusHistorikk} siden det ikke er sendt Done-melding " +
                    "for tidligere bestilte meldinger.",
            )
            return false
        }

        val fom = vedtaksperioder.finnSykefraværStart(inntektsmeldingMedStatus.vedtakFom)

        bestillBeskjed(inntektsmeldingMedStatus, fom)

        bestillMelding(inntektsmeldingMedStatus, fom)
        return true
    }

    private fun bestillBeskjed(
        inntektsmeldingMedStatus: InntektsmeldingMedStatus,
        fom: LocalDate,
    ) {
        val bestillingId =
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                ),
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

    private fun bestillMelding(
        inntektsmeldingMedStatus: InntektsmeldingMedStatus,
        fom: LocalDate,
    ) {
        val bestillingId =
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
                ),
            ).id!!

        meldingKafkaProducer.produserMelding(
            meldingUuid = bestillingId,
            meldingKafkaDto =
                MeldingKafkaDto(
                    fnr = inntektsmeldingMedStatus.fnr,
                    opprettMelding =
                        OpprettMelding(
                            tekst =
                                "Saksbehandlingen er forsinket fordi vi mangler inntektsmeldingen " +
                                    "fra ${inntektsmeldingMedStatus.orgNavn} for sykefraværet som startet ${
                                        fom.format(
                                            norskDateFormat,
                                        )
                                    }.",
                            lenke = inntektsmeldingManglerUrl,
                            variant = Variant.INFO,
                            lukkbar = false,
                            synligFremTil = synligFremTil(),
                            meldingType = "MANGLENDE_INNTEKTSMELDING",
                        ),
                ),
        )

        log.info("Bestilte melding for manglende inntektsmelding ${inntektsmeldingMedStatus.eksternId}")
    }
}
