package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
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
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit.DAYS

@Component
class ForsinketSaksbehandlingFørsteVarselFinnPersoner(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val forsinketSaksbehandlingVarslingFørsteVarsel: ForsinketSaksbehandlingVarslingFørsteVarsel,
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()
    private val varselGrense = if (environmentToggles.isProduction()) 120 else 4
    private val funksjonellGrenseForAntallVarsler = if (environmentToggles.isProduction()) 2000 else 7

    fun hentOgProsseser(now: Instant): Map<CronJobStatus, Int> {
        val sendtFoer = now.atOffset(ZoneOffset.UTC).minusDays(28).toInstant()

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for forsinket saksbehandling grunnet manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] = fnrListe.size

        fnrListe.map { fnr ->
            forsinketSaksbehandlingVarslingFørsteVarsel.prosesserForsteForsinketSaksbehandlingVarsel(
                fnr,
                sendtFoer,
                dryRun = true,
                now = now,
            )
        }.dryRunSjekk(funksjonellGrenseForAntallVarsler, CronJobStatus.SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING)
            .also { returMap[CronJobStatus.FØRSTE_FORSINKET_SAKSBEHANDLING_VARSEL_DRY_RUN] = it }

        fnrListe.forEachIndexed { idx, fnr ->
            forsinketSaksbehandlingVarslingFørsteVarsel.prosesserForsteForsinketSaksbehandlingVarsel(
                fnr,
                sendtFoer,
                false,
                now = now,
            )
                .also {
                    returMap.increment(it)
                }

            val antallSendteVarsler = returMap[CronJobStatus.SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING]
            if (antallSendteVarsler != null && antallSendteVarsler >= varselGrense) {
                returMap[CronJobStatus.THROTTLET_FØRSTE_FORSINKER_SAKSBEHANDLING_VARSEL] = fnrListe.size - idx - 1
                return returMap
            }
        }
        return returMap
    }
}

@Component
class ForsinketSaksbehandlingVarslingFørsteVarsel(
    private val hentAltForPerson: HentAltForPerson,
    private val lockRepository: LockRepository,
    private val brukervarsel: Brukervarsel,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val inntektesmeldingRepository: InntektsmeldingRepository,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosesserForsteForsinketSaksbehandlingVarsel(
        fnr: String,
        sendtFoer: Instant,
        dryRun: Boolean,
        now: Instant,
    ): CronJobStatus {
        if (!dryRun) {
            lockRepository.settAdvisoryTransactionLock(fnr)
        }

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val varslerAlleredeOmVenterSbPaaPeriode =
            allePerioder.any {
                listOf(
                    VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                    REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
                ).contains(it.vedtaksperiode.sisteVarslingstatus)
            }
        if (varslerAlleredeOmVenterSbPaaPeriode) {
            return CronJobStatus.VARSLER_ALLEREDE_OM_VENTER_PA_SAKSBEHANDLER
        }

        val forstePerArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == VENTER_PÅ_SAKSBEHANDLER }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }
                .groupBy { it.soknader.sortedBy { it.sendt }.last().orgnummer }
                .map { it.value.sortedBy { it.soknader.sortedBy { it.sendt }.first().fom } }
                .map { it.first() }
                .filter {
                    it.vedtaksperiode.sisteVarslingstatus == null ||
                        listOf(
                            VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE,
                            VARSLET_MANGLER_INNTEKTSMELDING_ANDRE_DONE,
                        ).contains(it.vedtaksperiode.sisteVarslingstatus)
                }
                .sortedBy { it.soknader.first().orgnummer }

        // hvis varsel på nummer en så setter vi egen status på de andre orgnumrene
        val nyligVarslet =
            allePerioder
                .flatMap { it.statuser }
                .filter { it.tidspunkt.isAfter(now.minus(12, DAYS)) }
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
        if (forstePerArbeidsgiver.isEmpty()) {
            return CronJobStatus.INGEN_PERIODE_FUNNET_FOR_FØRSTE_FORSINKET_SAKSBEHANDLING_VARSEL
        }
        var harSendtEtVarsel = false
        forstePerArbeidsgiver.forEachIndexed { idx, perioden ->
            val soknaden = perioden.soknader.sortedBy { it.sendt }.last()

            if (harSendtEtVarsel) {
                if (!dryRun) {
                    vedtaksperiodeBehandlingStatusRepository.save(
                        VedtaksperiodeBehandlingStatusDbRecord(
                            vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                            opprettetDatabase = now,
                            tidspunkt = now,
                            status = VARSLET_FORSINKET_PA_ANNEN_ORGNUMMER,
                            dittSykefravaerMeldingId = null,
                            brukervarselId = null,
                        ),
                    )
                    vedtaksperiodeBehandlingRepository.save(
                        perioden.vedtaksperiode.copy(
                            sisteVarslingstatus = VARSLET_FORSINKET_PA_ANNEN_ORGNUMMER,
                            sisteVarslingstatusTidspunkt = now,
                            oppdatertDatabase = now,
                        ),
                    )
                }

                return@forEachIndexed
            }

            val randomGenerator =
                SeededUuid(perioden.statuser.first { it.status == VENTER_PÅ_SAKSBEHANDLER }.id!!)
            val inntektsmeldinger = inntektesmeldingRepository.findByFnrIn(listOf(fnr))

            val inntektsmelding =
                finnLikesteInntektsmelding(inntektsmeldinger, perioden, soknaden)
            if (inntektsmelding == null) {
                log.info(
                    "Fant ikke inntektsmelding for vedtaksperiodeId ${perioden.vedtaksperiode.vedtaksperiodeId} " +
                        "med start syketilfelle ${soknaden.startSyketilfelle}",
                )
                return CronJobStatus.FANT_INGEN_INNTEKTSMELDING
            }

            if (inntektsmelding.fullRefusjon) {
                if (!dryRun) {
                    vedtaksperiodeBehandlingStatusRepository.save(
                        VedtaksperiodeBehandlingStatusDbRecord(
                            vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                            opprettetDatabase = now,
                            tidspunkt = now,
                            status = VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
                            brukervarselId = null,
                            dittSykefravaerMeldingId = null,
                        ),
                    )

                    vedtaksperiodeBehandlingRepository.save(
                        perioden.vedtaksperiode.copy(
                            sisteVarslingstatus = VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
                            sisteVarslingstatusTidspunkt = now,
                            oppdatertDatabase = now,
                        ),
                    )
                }
                return CronJobStatus.VARSLER_IKKE_GRUNNET_FULL_REFUSJON
            }
            harSendtEtVarsel = true
            if (!dryRun) {
                val brukervarselId = randomGenerator.nextUUID()

                log.info("Sender første forsinket saksbehandling varsel til vedtaksperiode ${perioden.vedtaksperiode.vedtaksperiodeId}")

                val varselTekst = skapForsinketSaksbehandling28Tekst(soknaden.startSyketilfelle)
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
                                    meldingType = "FORSINKET_SAKSBEHANDLING_FORSTE_VARSEL",
                                ),
                        ),
                )

                vedtaksperiodeBehandlingStatusRepository.save(
                    VedtaksperiodeBehandlingStatusDbRecord(
                        vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                        opprettetDatabase = now,
                        tidspunkt = now,
                        status = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                        brukervarselId = brukervarselId,
                        dittSykefravaerMeldingId = meldingBestillingId,
                    ),
                )

                vedtaksperiodeBehandlingRepository.save(
                    perioden.vedtaksperiode.copy(
                        sisteVarslingstatus = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                        sisteVarslingstatusTidspunkt = now,
                        oppdatertDatabase = now,
                    ),
                )
            }
        }

        return CronJobStatus.SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING
    }
}
