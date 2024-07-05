package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.SeededUuid
import no.nav.helse.flex.util.increment
import no.nav.helse.flex.varseltekst.skapVenterPåInntektsmelding15Tekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime



@Component
class ManglendeInntektsmeldingFørsteVarselFinnPersoner(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val manglendeInntektsmeldingFørsteVarsel: ManglendeInntektsmeldingFørsteVarsel,
    environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    private val maxAntallUtsendelsePerKjoring = if (environmentToggles.isProduction()) 250 else 4
    private val funksjonellGrenseForAntallVarsler = if (environmentToggles.isProduction()) 2000 else 7

    fun hentOgProsseser(now: OffsetDateTime): Map<CronJobStatus, Int> {
        val sendtFoer = now.minusDays(15).toInstant()

        val fnrListe =
            vedtaksperiodeBehandlingRepository
                .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtFoer = sendtFoer)

        val returMap = mutableMapOf<CronJobStatus, Int>()
        log.info("Fant ${fnrListe.size} unike fnr for varselutsending for manglende inntektsmelding")

        returMap[CronJobStatus.UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] = fnrListe.size

        fnrListe.map { fnr ->
            manglendeInntektsmeldingFørsteVarsel.prosseserManglendeInntektsmeldingKandidat(
                fnr,
                sendtFoer,
                dryRun = true,
            )
        }.dryRunSjekk(funksjonellGrenseForAntallVarsler, CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15)

        fnrListe.forEachIndexed { idx, fnr ->
            manglendeInntektsmeldingFørsteVarsel.prosseserManglendeInntektsmeldingKandidat(fnr, sendtFoer, dryRun = false)
                .also {
                    returMap.increment(it)
                }
            val antallSendteVarsler = returMap[CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15]
            if (antallSendteVarsler != null && antallSendteVarsler >= maxAntallUtsendelsePerKjoring) {
                returMap[CronJobStatus.UTELATTE_FNR_MANGLER_IM_15_THROTTLE] = fnrListe.size - idx - 1
                return returMap
            }
        }

        return returMap
    }
}


@Component
class ManglendeInntektsmeldingFørsteVarsel(
    private val hentAltForPerson: HentAltForPerson,
    private val lockRepository: LockRepository,
    private val brukervarsel: Brukervarsel,
    private val organisasjonRepository: OrganisasjonRepository,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
) {
    val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserManglendeInntektsmeldingKandidat(
        fnr: String,
        sendtFoer: Instant,
        dryRun: Boolean,
    ): CronJobStatus {
        if (!dryRun) {
            lockRepository.settAdvisoryTransactionLock(fnr)
        }

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val venterPaaArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }
                .groupBy { it.soknader.sortedBy { it.sendt }.last().orgnummer }
                .map { it.value.sortedBy { it.soknader.sortedBy { it.sendt }.first().fom } }
                .map { it.first() }
                .filter { it.vedtaksperiode.sisteVarslingstatus == null }

        if (venterPaaArbeidsgiver.isEmpty()) {
            return CronJobStatus.INGEN_PERIODE_FUNNET_FOR_VARSEL_MANGLER_INNTEKTSMELDING_15
        }

        if (!dryRun) {
            venterPaaArbeidsgiver.forEachIndexed { idx, perioden ->
                val soknaden = perioden.soknader.sortedBy { it.sendt }.last()

                val randomGenerator =
                    SeededUuid(perioden.statuser.first { it.status == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }.id!!)

                val brukervarselId = randomGenerator.nextUUID()

                val orgnavn = organisasjonRepository.findByOrgnummer(soknaden.orgnummer!!)?.navn ?: soknaden.orgnummer

                val synligFremTil = OffsetDateTime.now().plusMonths(4).toInstant()

                log.info("Sender første mangler inntektsmelding varsel til vedtaksperiode ${perioden.vedtaksperiode.vedtaksperiodeId}")

                brukervarsel.beskjedManglerInntektsmelding(
                    fnr = fnr,
                    bestillingId = brukervarselId,
                    orgNavn = orgnavn,
                    fom = soknaden.startSyketilfelle,
                    synligFremTil = synligFremTil,
                    forsinketSaksbehandling = false,
                    brukEksternVarsling = idx == 0,
                )

                val meldingBestillingId = randomGenerator.nextUUID()
                meldingKafkaProducer.produserMelding(
                    meldingUuid = meldingBestillingId,
                    meldingKafkaDto =
                    MeldingKafkaDto(
                        fnr = fnr,
                        opprettMelding =
                        OpprettMelding(
                            tekst = skapVenterPåInntektsmelding15Tekst(soknaden.startSyketilfelle, orgnavn),
                            lenke = inntektsmeldingManglerUrl,
                            variant = Variant.INFO,
                            lukkbar = false,
                            synligFremTil = synligFremTil,
                            meldingType = "MANGLENDE_INNTEKTSMELDING",
                        ),
                    ),
                )

                vedtaksperiodeBehandlingStatusRepository.save(
                    VedtaksperiodeBehandlingStatusDbRecord(
                        vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                        opprettetDatabase = Instant.now(),
                        tidspunkt = Instant.now(),
                        status = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
                        brukervarselId = brukervarselId,
                        dittSykefravaerMeldingId = meldingBestillingId,
                    ),
                )

                vedtaksperiodeBehandlingRepository.save(
                    perioden.vedtaksperiode.copy(
                        sisteVarslingstatus = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
                        sisteVarslingstatusTidspunkt = Instant.now(),
                        oppdatertDatabase = Instant.now(),
                    ),
                )
            }
        }
        return CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15
    }
}

