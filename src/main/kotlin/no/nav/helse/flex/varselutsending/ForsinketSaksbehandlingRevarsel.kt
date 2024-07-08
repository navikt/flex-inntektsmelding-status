package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.SeededUuid
import no.nav.helse.flex.util.increment
import no.nav.helse.flex.varseltekst.SAKSBEHANDLINGSTID_URL
import no.nav.helse.flex.varseltekst.skapForsinketSaksbehandling28Tekst
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

@Component
class ForsinketSaksbehandlingRevarselFinnPersoner(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forsinketSaksbehandlingVarslingRevarsel: ForsinketSaksbehandlingVarslingRevarsel,
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()
    private val varselGrense = if (environmentToggles.isProduction()) 120 else 4
    private val funksjonellGrenseForAntallVarsler = if (environmentToggles.isProduction()) 2000 else 7

    fun hentOgProsseser(now: OffsetDateTime): Map<CronJobStatus, Int> {
        val varsletFør = now.minusDays(28).toInstant()

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerForRevarslingSomVenterPåSaksbehandlger(varsletFoer = varsletFør)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_REVARSEL_FORSINKET_SAKSBEHANDLING] = fnrListe.size

        fnrListe.map { fnr ->
            forsinketSaksbehandlingVarslingRevarsel.prosseserRevarsel(
                fnr,
                varsletFør,
                dryRun = true,
            )
        }.dryRunSjekk(funksjonellGrenseForAntallVarsler, SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING)
            .also { returMap[CronJobStatus.REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL_DRY_RUN] = it }

        fnrListe.forEachIndexed { idx, fnr ->
            forsinketSaksbehandlingVarslingRevarsel.prosseserRevarsel(fnr, varsletFør, false)
                .also {
                    returMap.increment(it)
                }

            val antallSendteVarsler = returMap[SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING]
            if (antallSendteVarsler != null && antallSendteVarsler >= varselGrense) {
                returMap[CronJobStatus.THROTTLET_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL] = fnrListe.size - idx - 1
                return returMap
            }
        }
        return returMap
    }
}

@Component
class ForsinketSaksbehandlingVarslingRevarsel(
    private val hentAltForPerson: HentAltForPerson,
    private val lockRepository: LockRepository,
    private val brukervarsel: Brukervarsel,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val meldingOgBrukervarselDone: MeldingOgBrukervarselDone,
    @Value("\${MINIMUMSTID_FRA_VARSEL_TIL_FORSTE_FORSINKET_SAKSBEHANDLING_VARSEL}") private val minimumstid: String,
) {
    private val log = logger()
    val duration = Duration.parse(minimumstid)

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserRevarsel(
        fnr: String,
        varsletFør: Instant,
        dryRun: Boolean,
    ): CronJobStatus {
        if (!dryRun) {
            lockRepository.settAdvisoryTransactionLock(fnr)
        }

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val nyligVarslet =
            allePerioder
                .flatMap { it.statuser }
                .filter { it.tidspunkt.isAfter(Instant.now().minus(duration)) }
                .any {
                    listOf(
                        VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                        REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                        VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
                        VARSLET_MANGLER_INNTEKTSMELDING_ANDRE,
                    ).contains(it.status)
                }

        if (nyligVarslet) {
            return CronJobStatus.HAR_FATT_NYLIG_VARSEL
        }

        val revarslingsperioder =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == VENTER_PÅ_SAKSBEHANDLER }
                .filter {
                    listOf(
                        REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                        VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                    ).contains(it.vedtaksperiode.sisteVarslingstatus)
                }
                .filter { it.vedtaksperiode.sisteVarslingstatusTidspunkt?.isBefore(varsletFør) == true }

        if (revarslingsperioder.size > 1) {
            log.error(
                "Fant ${revarslingsperioder.size} perioder for revarsel for vedtaksperioder " +
                    "${revarslingsperioder.map { it.vedtaksperiode.vedtaksperiodeId }}",
            )
            // Dette skal ikke skje
            return CronJobStatus.FANT_FLERE_ENN_EN_VEDTAKSPERIODE_FOR_REVARSEL
        }

        // Forvent en revarslingsperiode. log error og returner egen status hvis ikke riktig
        val revarslingsperiode = revarslingsperioder.firstOrNull()
        if (revarslingsperiode == null) {
            log.error("Fant ingen perioder for revarsel for fnr $fnr")
            return CronJobStatus.INGEN_PERIODE_FUNNET_FOR_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL
        }

        if (!dryRun) {
            val randomGenerator = SeededUuid(revarslingsperiode.statuser.minByOrNull { it.tidspunkt }!!.id!!)
            meldingOgBrukervarselDone.doneForsinketSbVarsel(revarslingsperiode.vedtaksperiode, fnr)
            val brukervarselId = randomGenerator.nextUUID()

            log.info(
                "Revarsler forsinket saksbehandling til vedtaksperiode ${revarslingsperiode.vedtaksperiode.vedtaksperiodeId}",
            )

            val synligFremTil = OffsetDateTime.now().plusMonths(4).toInstant()
            brukervarsel.beskjedForsinketSaksbehandling(
                fnr = fnr,
                bestillingId = brukervarselId,
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
                                lenke = SAKSBEHANDLINGSTID_URL,
                                variant = Variant.INFO,
                                lukkbar = false,
                                synligFremTil = synligFremTil,
                                meldingType = "FORSINKET_SAKSBEHANDLING_REVARSEL",
                            ),
                    ),
            )

            vedtaksperiodeBehandlingStatusRepository.save(
                VedtaksperiodeBehandlingStatusDbRecord(
                    vedtaksperiodeBehandlingId = revarslingsperiode.vedtaksperiode.id!!,
                    opprettetDatabase = Instant.now(),
                    tidspunkt = Instant.now(),
                    status = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                    brukervarselId = brukervarselId,
                    dittSykefravaerMeldingId = meldingBestillingId,
                ),
            )

            vedtaksperiodeBehandlingRepository.save(
                revarslingsperiode.vedtaksperiode.copy(
                    sisteVarslingstatus = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                    sisteVarslingstatusTidspunkt = Instant.now(),
                    oppdatertDatabase = Instant.now(),
                ),
            )
        }

        return SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING
    }
}
