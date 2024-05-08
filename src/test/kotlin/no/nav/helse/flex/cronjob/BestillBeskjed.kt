package no.nav.helse.flex.cronjob

import no.nav.helse.flex.brukernotifikasjon.Brukernotifikasjon
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.erRettFør
import no.nav.helse.flex.util.finnSykefraværStart
import no.nav.helse.flex.varseltekst.skapVenterPåInntektsmeldingTekst
import no.nav.helse.flex.vedtaksperiode.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class BestillBeskjed(
    private val statusRepository: StatusRepository,
    private val vedtaksperiodeStatusRepository: VedtaksperiodeStatusRepository,
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
    fun opprettVarsler(vedtaksperiodeMedStatus: VedtaksperiodeMedStatus): Boolean {
        lockRepository.settAdvisoryTransactionLock(vedtaksperiodeMedStatus.fnr.toLong())

        val vedtaksperioder =
            statusRepository.hentAlleForPerson(
                fnr = vedtaksperiodeMedStatus.fnr,
                orgNr = vedtaksperiodeMedStatus.orgNr,
            )
        if (vedtaksperioder.overlapper()) {
            log.info("Fant overlappende perioder for id ${vedtaksperiodeMedStatus.id}. Vet ikke hva jeg skal gjøre. Hopper over denne.")
            vedtaksperiodeStatusRepository.save(
                VedtaksperiodeStatusDbRecord(
                    vedtaksperiodeDbId = vedtaksperiodeMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.OVELAPPER_SENDER_IKKE_UT,
                ),
            )
            return false
        }
        if (vedtaksperioder.manglendeInntektsmeldingOverlapperBehandlesUtaforSpleis()) {
            log.info(
                "Fant overlappende perioder med manglende inntektsmelding ${vedtaksperiodeMedStatus.id} som behandles " +
                    "utenfor Spleis. Vet ikke hva jeg skal gjøre. Hopper over denne.",
            )
            vedtaksperiodeStatusRepository.save(
                VedtaksperiodeStatusDbRecord(
                    vedtaksperiodeDbId = vedtaksperiodeMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.OVELAPPER_BEHANDLES_UTAFOR_SPLEIS_SENDER_IKKE_UT,
                ),
            )
            return false
        }

        val harManglendeImPeriodeRettForan =
            vedtaksperioder
                .filter { it.status == StatusVerdi.MANGLER_INNTEKTSMELDING }
                .any { it.vedtakTom.erRettFør(vedtaksperiodeMedStatus.vedtakFom) }

        if (harManglendeImPeriodeRettForan) {
            vedtaksperiodeStatusRepository.save(
                VedtaksperiodeStatusDbRecord(
                    vedtaksperiodeDbId = vedtaksperiodeMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.HAR_PERIODE_RETT_FOER,
                ),
            )
            return false
        }

        val inntektsmeldingMedStatusHistorikk =
            statusRepository.hentVedtaksperiodeMedStatusHistorikk(vedtaksperiodeMedStatus.id)!!

        if (!inntektsmeldingMedStatusHistorikk.alleBrukernotifikasjonerErDonet()) {
            log.warn(
                "Bestiller ikke beskjed for inntektsmelding med eksternId ${inntektsmeldingMedStatusHistorikk.eksternId} og " +
                    "statuser ${inntektsmeldingMedStatusHistorikk.statusHistorikk} siden det ikke er sendt Done-melding " +
                    "for tidligere bestilte meldinger.",
            )
            return false
        }

        val fom = vedtaksperioder.finnSykefraværStart(vedtaksperiodeMedStatus.vedtakFom)

        bestillBeskjed(vedtaksperiodeMedStatus, fom)

        bestillMelding(vedtaksperiodeMedStatus, fom)
        return true
    }

    private fun bestillBeskjed(
        vedtaksperiodeMedStatus: VedtaksperiodeMedStatus,
        fom: LocalDate,
    ) {
        val bestillingId =
            vedtaksperiodeStatusRepository.save(
                VedtaksperiodeStatusDbRecord(
                    vedtaksperiodeDbId = vedtaksperiodeMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                ),
            ).id!!

        brukernotifikasjon.beskjedManglerInntektsmelding(
            fnr = vedtaksperiodeMedStatus.fnr,
            eksternId = vedtaksperiodeMedStatus.eksternId,
            bestillingId = bestillingId,
            orgNavn = vedtaksperiodeMedStatus.orgNavn,
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
        vedtaksperiodeMedStatus: VedtaksperiodeMedStatus,
        fom: LocalDate,
    ) {
        val bestillingId =
            vedtaksperiodeStatusRepository.save(
                VedtaksperiodeStatusDbRecord(
                    vedtaksperiodeDbId = vedtaksperiodeMedStatus.id,
                    opprettet = Instant.now(),
                    status = StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
                ),
            ).id!!

        meldingKafkaProducer.produserMelding(
            meldingUuid = bestillingId,
            meldingKafkaDto =
                MeldingKafkaDto(
                    fnr = vedtaksperiodeMedStatus.fnr,
                    opprettMelding =
                        OpprettMelding(
                            tekst = skapVenterPåInntektsmeldingTekst(fom, vedtaksperiodeMedStatus.orgNavn),
                            lenke = inntektsmeldingManglerUrl,
                            variant = Variant.INFO,
                            lukkbar = false,
                            synligFremTil = synligFremTil(),
                            meldingType = "MANGLENDE_INNTEKTSMELDING",
                        ),
                ),
        )

        log.info("Bestilte melding for manglende inntektsmelding ${vedtaksperiodeMedStatus.eksternId}")
    }
}
