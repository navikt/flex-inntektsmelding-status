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
import no.nav.helse.flex.varseltekst.skapRevarselForsinketSaksbehandlingTekst
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.DAYS

@Component
class ForsinketSaksbehandlingRevarselFinnPersoner(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forsinketSaksbehandlingVarslingRevarsel: ForsinketSaksbehandlingVarslingRevarsel,
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()
    private val varselGrense = if (environmentToggles.isProduction()) 120 else 4
    private val funksjonellGrenseForAntallVarsler = if (environmentToggles.isProduction()) 2000 else 7

    fun hentOgProsseser(now: Instant): Map<CronJobStatus, Int> {
        val varsletFør = now.minus(28, DAYS)

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerForRevarslingSomVenterPåSaksbehandler(varsletFoer = varsletFør)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_REVARSEL_FORSINKET_SAKSBEHANDLING] = fnrListe.size

        fnrListe.map { fnr ->
            forsinketSaksbehandlingVarslingRevarsel.prosseserRevarsel(
                fnr,
                varsletFør,
                dryRun = true,
                now = now,
            )
        }.dryRunSjekk(funksjonellGrenseForAntallVarsler, SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING)
            .also { returMap[CronJobStatus.REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL_DRY_RUN] = it }

        fnrListe.forEachIndexed { idx, fnr ->
            forsinketSaksbehandlingVarslingRevarsel.prosseserRevarsel(fnr, varsletFør, false, now)
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
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserRevarsel(
        fnr: String,
        varsletFør: Instant,
        dryRun: Boolean,
        now: Instant,
    ): CronJobStatus {
        if (environmentToggles.isProduction() && !dryRun) {
            return CronJobStatus.REVARSLING_DISABLET_I_PRODUKSJON
        }

        if (!dryRun) {
            lockRepository.settAdvisoryTransactionLock(fnr)
        }

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val nyligVarslet =
            allePerioder
                .flatMap { it.statuser }
                .filter { it.tidspunkt.isAfter(now.minus(28, DAYS)) }
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

        // Sorter og velg eldste revaslingsperiode basert på sisteVarslingstatusTidspunkt
        val revarslingsperiode = revarslingsperioder.minByOrNull { it.vedtaksperiode.sisteVarslingstatusTidspunkt!! }

        if (revarslingsperiode == null) {
            log.error("Fant ingen perioder for revarsel for fnr $fnr")
            return CronJobStatus.INGEN_PERIODE_FUNNET_FOR_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL
        }

        if (!dryRun) {
            val randomGenerator = SeededUuid(revarslingsperiode.statuser.maxByOrNull { it.tidspunkt }!!.id!!)
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
                revarsel = true,
            )

            val meldingBestillingId = randomGenerator.nextUUID()
            meldingKafkaProducer.produserMelding(
                meldingUuid = meldingBestillingId,
                meldingKafkaDto =
                    MeldingKafkaDto(
                        fnr = fnr,
                        opprettMelding =
                            OpprettMelding(
                                tekst = skapRevarselForsinketSaksbehandlingTekst(),
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
                    opprettetDatabase = now,
                    tidspunkt = now,
                    status = REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                    brukervarselId = brukervarselId,
                    dittSykefravaerMeldingId = meldingBestillingId,
                ),
            )

            vedtaksperiodeBehandlingRepository.save(
                revarslingsperiode.vedtaksperiode.copy(
                    sisteVarslingstatus = REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                    sisteVarslingstatusTidspunkt = now,
                    oppdatertDatabase = now,
                ),
            )
        }

        return SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING
    }
}
