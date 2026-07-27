package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.SeededUuid
import no.nav.helse.flex.util.increment
import no.nav.helse.flex.util.ventPaAlle
import no.nav.helse.flex.varseltekst.skapVenterPåInntektsmelding28Tekst
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
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
class ManglendeInntektsmeldingAndreVarselFinnPersoner(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val manglendeInntektsmeldingAndreVarsel: ManglendeInntektsmeldingAndreVarsel,
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()
    private val maxAntallUtsendelsePerKjoring = if (environmentToggles.isNais()) 250 else 4
    private val funksjonellGrenseForAntallVarsler = if (environmentToggles.isNais()) 4000 else 7

    fun hentOgProsseser(now: Instant): Map<CronJobStatus, Int> {
        val sendtFoer = now.minus(28, DAYS)

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerMedForsinketSaksbehandlingGrunnetManglendeInntektsmelding(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] = fnrListe.size

        fnrListe
            .map { fnr ->
                manglendeInntektsmeldingAndreVarsel.prosseserManglendeInntektsmeldingAndreVarsel(
                    fnr = fnr,
                    sendtFoer = sendtFoer,
                    dryRun = true,
                    now = now,
                )
            }.let { ventPaAlle(it) }
            .dryRunSjekk(funksjonellGrenseForAntallVarsler, SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING)
            .also { returMap[CronJobStatus.ANDRE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN] = it }

        val sendtTeller = AtomicInteger(0)
        fnrListe
            .map { fnr ->
                manglendeInntektsmeldingAndreVarsel.prosseserManglendeInntektsmeldingAndreVarsel(
                    fnr = fnr,
                    sendtFoer = sendtFoer,
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
class ManglendeInntektsmeldingAndreVarsel(
    private val hentAltForPerson: HentAltForPerson,
    private val lockRepository: LockRepository,
    private val meldingOgBrukervarselDone: MeldingOgBrukervarselDone,
    private val brukervarsel: Brukervarsel,
    private val organisasjonRepository: OrganisasjonRepository,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    @param:Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
) {
    @Async("varselutsendingTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserManglendeInntektsmeldingAndreVarsel(
        fnr: String,
        sendtFoer: Instant,
        dryRun: Boolean,
        now: Instant,
        sendtTeller: AtomicInteger? = null,
        maxAntallUtsendelse: Int = Int.MAX_VALUE,
    ): CompletableFuture<CronJobStatus> {
        if (!dryRun) {
            requireNotNull(sendtTeller) { "sendtTeller må være satt når dryRun er false" }
            if (sendtTeller.get() >= maxAntallUtsendelse) {
                return CompletableFuture.completedFuture(CronJobStatus.THROTTLET_ANDRE_MANGLER_INNTEKTSMELDING_VARSEL)
            }
            lockRepository.settAdvisoryTransactionLock(fnr)
        }

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val venterPaaArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }
                .filter { it.vedtaksperiode.sisteVarslingstatus == StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }

        if (venterPaaArbeidsgiver.isEmpty()) {
            return CompletableFuture.completedFuture(CronJobStatus.INGEN_PERIODE_FUNNET_FOR_ANDER_MANGLER_INNTEKTSMELDING_VARSEL)
        }

        val harFattVarselNylig =
            venterPaaArbeidsgiver
                .mapNotNull { it.vedtaksperiode.sisteVarslingstatusTidspunkt }
                .any { it.isAfter(now.minus(10, DAYS)) }

        if (harFattVarselNylig) {
            return CompletableFuture.completedFuture(CronJobStatus.HAR_FATT_NYLIG_VARSEL)
        }

        if (!dryRun) {
            if (sendtTeller!!.incrementAndGet() > maxAntallUtsendelse) {
                return CompletableFuture.completedFuture(CronJobStatus.THROTTLET_ANDRE_MANGLER_INNTEKTSMELDING_VARSEL)
            }
            venterPaaArbeidsgiver.forEachIndexed { idx, perioden ->
                val soknaden = perioden.soknader.sortedBy { it.sendt }.last()

                meldingOgBrukervarselDone.doneSendteManglerImVarsler(perioden.vedtaksperiode, fnr)

                val randomGenerator =
                    SeededUuid(perioden.vedtaksperiode.id!!, StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_ANDRE)

                val brukervarselId = randomGenerator.nextUUID()

                val orgnavn =
                    if (soknaden.orgnummer == null) {
                        if (soknaden.soknadstype == SoknadstypeDTO.ARBEIDSTAKERE.name) {
                            throw RuntimeException("Søknad ${soknaden.sykepengesoknadUuid} har ingen orgnummer")
                        }
                        "arbeidsgiver"
                    } else {
                        organisasjonRepository.findByOrgnummer(soknaden.orgnummer)?.navn ?: soknaden.orgnummer
                    }

                val synligFremTil = OffsetDateTime.now().plusMonths(4).toInstant()
                val varselTekst = skapVenterPåInntektsmelding28Tekst(orgnavn)

                brukervarsel.beskjedManglerInntektsmelding(
                    fnr = fnr,
                    bestillingId = brukervarselId,
                    synligFremTil = synligFremTil,
                    brukEksternVarsling = idx == 0,
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
                                    lenke = inntektsmeldingManglerUrl,
                                    variant = Variant.INFO,
                                    lukkbar = false,
                                    synligFremTil = synligFremTil,
                                    meldingType = "MANGLENDE_INNTEKTSMELDING_28",
                                ),
                        ),
                )

                vedtaksperiodeBehandlingStatusRepository.save(
                    VedtaksperiodeBehandlingStatusDbRecord(
                        vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id,
                        opprettetDatabase = now,
                        tidspunkt = now,
                        status = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_ANDRE,
                        brukervarselId = brukervarselId,
                        dittSykefravaerMeldingId = meldingBestillingId,
                    ),
                )

                vedtaksperiodeBehandlingRepository.save(
                    perioden.vedtaksperiode.copy(
                        sisteVarslingstatus = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_ANDRE,
                        sisteVarslingstatusTidspunkt = now,
                        oppdatertDatabase = now,
                    ),
                )
            }
        }

        return CompletableFuture.completedFuture(SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING)
    }
}
