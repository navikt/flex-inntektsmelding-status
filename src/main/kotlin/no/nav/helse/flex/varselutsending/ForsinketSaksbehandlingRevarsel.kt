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
import no.nav.helse.flex.util.ventPaAlle
import no.nav.helse.flex.varseltekst.SAKSBEHANDLINGSTID_URL
import no.nav.helse.flex.varseltekst.skapRevarselForsinketSaksbehandlingTekst
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import no.nav.helse.flex.vedtaksperiodebehandling.SpleisStatus
import no.nav.helse.flex.vedtaksperiodebehandling.VarslingStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.DAYS
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

@Component
class ForsinketSaksbehandlingRevarselFinnPersoner(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forsinketSaksbehandlingVarslingRevarsel: ForsinketSaksbehandlingVarslingRevarsel,
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()
    private val maxAntallUtsendelsePerKjoring = if (environmentToggles.isNais()) 120 else 4
    private val funksjonellGrenseForAntallVarsler = if (environmentToggles.isNais()) 2000 else 7

    fun hentOgProsseser(now: Instant): Map<CronJobStatus, Int> {
        val varsletFør = now.minus(28, DAYS)

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerForRevarslingSomVenterPåSaksbehandler(varsletFoer = varsletFør)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr-kandidater som vil vurderes for varselutsending av revarsel for forsinket saksbehandling")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_REVARSEL_FORSINKET_SAKSBEHANDLING] = fnrListe.size

        fnrListe
            .map { fnr ->
                forsinketSaksbehandlingVarslingRevarsel.prosseserRevarsel(
                    fnr = fnr,
                    varsletFør = varsletFør,
                    dryRun = true,
                    now = now,
                )
            }.let { ventPaAlle(it) }
            .dryRunSjekk(funksjonellGrenseForAntallVarsler, SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING)
            .also { returMap[CronJobStatus.REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL_DRY_RUN] = it }

        val sendtTeller = AtomicInteger(0)
        fnrListe
            .map { fnr ->
                forsinketSaksbehandlingVarslingRevarsel.prosseserRevarsel(
                    fnr = fnr,
                    varsletFør = varsletFør,
                    dryRun = false,
                    now = now,
                    sendtTeller = sendtTeller,
                    maxAntallUtsendelse = maxAntallUtsendelsePerKjoring,
                )
            }.let { ventPaAlle(it) }
            .forEach { returMap.increment(it) }

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
) {
    private val log = logger()

    @Async("varselutsendingTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserRevarsel(
        fnr: String,
        varsletFør: Instant,
        dryRun: Boolean,
        now: Instant,
        sendtTeller: AtomicInteger? = null,
        maxAntallUtsendelse: Int = Int.MAX_VALUE,
    ): CompletableFuture<CronJobStatus> {
        if (!dryRun) {
            requireNotNull(sendtTeller) { "sendtTeller må være satt når dryRun er false" }
            if (sendtTeller.get() >= maxAntallUtsendelse) {
                return CompletableFuture.completedFuture(CronJobStatus.THROTTLET_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL)
            }
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
            return CompletableFuture.completedFuture(CronJobStatus.HAR_FATT_NYLIG_VARSEL)
        }

        val revarslingsperioder =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == SpleisStatus.VENTER_PÅ_SAKSBEHANDLER }
                .filter {
                    listOf(
                        REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                        VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                    ).contains(it.vedtaksperiode.sisteVarslingstatus)
                }.filter { it.vedtaksperiode.sisteVarslingstatusTidspunkt?.isBefore(varsletFør) == true }

        // Sorter og velg eldste revaslingsperiode basert på sisteVarslingstatusTidspunkt
        val revarslingsperiode = revarslingsperioder.minByOrNull { it.vedtaksperiode.sisteVarslingstatusTidspunkt!! }

        if (revarslingsperiode == null) {
            log.error("Fant ingen perioder for revarsel for forsinket saksbehandling")
            return CompletableFuture.completedFuture(CronJobStatus.INGEN_PERIODE_FUNNET_FOR_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL)
        }

        if (!dryRun) {
            if (sendtTeller!!.incrementAndGet() > maxAntallUtsendelse) {
                return CompletableFuture.completedFuture(CronJobStatus.THROTTLET_REVARSEL_FORSINKET_SAKSBEHANDLING_VARSEL)
            }
            val rundeNr = revarslingsperiode.statuser.count { it.status == REVARSLET_VENTER_PÅ_SAKSBEHANDLER }
            val randomGenerator =
                SeededUuid(
                    revarslingsperiode.vedtaksperiode.id!!,
                    REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                    rundeNr,
                )
            meldingOgBrukervarselDone.doneForsinketSbVarsel(revarslingsperiode.vedtaksperiode, fnr)
            val brukervarselId = randomGenerator.nextUUID()

            log.info(
                "Revarsler forsinket saksbehandling til vedtaksperiode ${revarslingsperiode.vedtaksperiode.vedtaksperiodeId}",
            )

            val varselTekst = skapRevarselForsinketSaksbehandlingTekst()
            val synligFremTil = OffsetDateTime.now().plusMonths(4).toInstant()

            brukervarsel.beskjedForsinketSaksbehandling(
                fnr = fnr,
                bestillingId = brukervarselId,
                synligFremTil = synligFremTil,
                varselTekst = varselTekst,
            )

            val meldingBestillingId = randomGenerator.nextUUID()
            meldingKafkaProducer.produserMelding(
                meldingUuid = meldingBestillingId,
                meldingKafkaDto =
                    MeldingKafkaDto(
                        fnr = fnr,
                        opprettMelding =
                            OpprettMelding(
                                tekst = varselTekst,
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
                    vedtaksperiodeBehandlingId = revarslingsperiode.vedtaksperiode.id,
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

        return CompletableFuture.completedFuture(SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING)
    }
}
