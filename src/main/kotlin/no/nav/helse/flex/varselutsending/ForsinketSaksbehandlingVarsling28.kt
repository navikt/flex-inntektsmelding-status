package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.SeededUuid
import no.nav.helse.flex.varseltekst.skapForsinketSaksbehandling28Tekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime

@Component
class ForsinketSaksbehandlingVarsling28(
    private val hentAltForPerson: HentAltForPerson,
    private val lockRepository: LockRepository,
    private val environmentToggles: EnvironmentToggles,
    private val meldingOgBrukervarselDone: MeldingOgBrukervarselDone,
    private val brukervarsel: Brukervarsel,
    private val organisasjonRepository: OrganisasjonRepository,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val inntektesmeldingRepository: InntektsmeldingRepository,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserManglendeInntektsmelding28(
        fnr: String,
        sendtFoer: Instant,
    ): CronJobStatus {
        if (environmentToggles.isProduction()) {
            return CronJobStatus.FORSINKET_SAKSBEHANDLING_VARSEL_28_DISABLET_I_PROD
        }
        lockRepository.settAdvisoryTransactionLock(fnr)

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val venterPaaArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_SAKSBEHANDLER }
                .filter { p ->
                    listOf(
                        StatusVerdi.VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                        StatusVerdi.REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                        StatusVerdi.VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
                    )
                        .none { it == p.vedtaksperiode.sisteVarslingstatus }
                }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }

        if (venterPaaArbeidsgiver.size != 1) {
            return CronJobStatus.FLERE_PERIODER_IKKE_IMPLEMENTERT
        }
        // TODO sjekk om vi nettopp har sendt noe annet?

        val perioden = venterPaaArbeidsgiver.first()
        val soknaden = perioden.soknader.sortedBy { it.sendt }.last()

        meldingOgBrukervarselDone.doneSendteManglerImVarsler(perioden.vedtaksperiode, fnr)

        val randomGenerator =
            SeededUuid(perioden.statuser.first { it.status == StatusVerdi.VENTER_PÅ_SAKSBEHANDLER }.id!!)

        val inntektsmelding =
            inntektesmeldingRepository.findByFnrIn(listOf(fnr)).matchInntektsmeldingMedPeriode(perioden)
                ?: return CronJobStatus.FORVENTET_EN_INNTEKTSMELDING_FANT_IKKE

        if (inntektsmelding.fullRefusjon) {
            vedtaksperiodeBehandlingStatusRepository.save(
                VedtaksperiodeBehandlingStatusDbRecord(
                    vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                    opprettetDatabase = Instant.now(),
                    tidspunkt = Instant.now(),
                    status = StatusVerdi.VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
                    brukervarselId = null,
                    dittSykefravaerMeldingId = null,
                ),
            )

            vedtaksperiodeBehandlingRepository.save(
                perioden.vedtaksperiode.copy(
                    sisteVarslingstatus = StatusVerdi.VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
                    sisteVarslingstatusTidspunkt = Instant.now(),
                    oppdatertDatabase = Instant.now(),
                ),
            )
            return CronJobStatus.VARSLER_IKKE_GRUNNET_FULL_REFUSJON
        }

        // TODO finn inntektsmelding

        val brukervarselId = randomGenerator.nextUUID()

        val orgnavn = organisasjonRepository.findByOrgnummer(soknaden.orgnummer!!)?.navn ?: soknaden.orgnummer

        val synligFremTil = OffsetDateTime.now().plusMonths(4).toInstant()
        brukervarsel.beskjedForsinketSaksbehandling(
            fnr = fnr,
            bestillingId = brukervarselId,
            orgNavn = orgnavn,
            synligFremTil = synligFremTil,
        )

        val meldingBestillingId = randomGenerator.nextUUID()
        meldingKafkaProducer.produserMelding(
            meldingUuid = meldingBestillingId,
            meldingKafkaDto =
                MeldingKafkaDto(
                    fnr = fnr,
                    opprettMelding =
                        OpprettMelding(
                            tekst = skapForsinketSaksbehandling28Tekst(),
                            lenke = null,
                            variant = Variant.INFO,
                            lukkbar = false,
                            synligFremTil = synligFremTil,
                            meldingType = "FORSINKET_SAKSBEHANDLING_28",
                            // TODO fiks meldingtype kanskje?
                        ),
                ),
        )

        vedtaksperiodeBehandlingStatusRepository.save(
            VedtaksperiodeBehandlingStatusDbRecord(
                vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                opprettetDatabase = Instant.now(),
                tidspunkt = Instant.now(),
                status = StatusVerdi.VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                brukervarselId = brukervarselId,
                dittSykefravaerMeldingId = meldingBestillingId,
            ),
        )

        vedtaksperiodeBehandlingRepository.save(
            perioden.vedtaksperiode.copy(
                sisteVarslingstatus = StatusVerdi.VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                sisteVarslingstatusTidspunkt = Instant.now(),
                oppdatertDatabase = Instant.now(),
            ),
        )

        return CronJobStatus.SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28
    }
}
