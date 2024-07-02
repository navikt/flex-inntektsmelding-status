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
import no.nav.helse.flex.varseltekst.SAKSBEHANDLINGSTID_URL
import no.nav.helse.flex.varseltekst.skapForsinketSaksbehandling28Tekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
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
    @Value("\${MINIMUMSTID_MELLOM_FORSINKET_SAKSBEHANDLING_VARSEL}") private val minimumstid: String,
) {
    private val log = logger()
    val duration = Duration.parse(minimumstid)

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

        val forstePerArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_SAKSBEHANDLER }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }
                .groupBy { it.soknader.sortedBy { it.sendt }.last().orgnummer }
                .map { it.value.sortedBy { it.soknader.sortedBy { it.sendt }.first().fom } }
                .map { it.first() }
                .filter {
                    it.vedtaksperiode.sisteVarslingstatus == null ||
                        listOf(
                            StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE,
                            StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_ANDRE_DONE,
                        ).contains(it.vedtaksperiode.sisteVarslingstatus)
                }
                .sortedBy { it.soknader.first().orgnummer }

        // hvis varsel på nummer en så setter vi egen status på de andre orgnumrene
        val nyligVarslet =
            allePerioder
                .flatMap { it.statuser }
                .filter { it.tidspunkt.isAfter(Instant.now().minus(duration)) }
                .any { it.status == StatusVerdi.VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE } // TODO også se etter revarsel senere

        if (nyligVarslet) {
            return CronJobStatus.FORSINKET_SAKSBEHANDLING_VARSEL_SENDT_SISTE_20_DAGER
        }
        if (forstePerArbeidsgiver.isEmpty()) {
            return CronJobStatus.INGEN_PERIODE_FUNNET_FOR_VARSEL_FORSINKET_SAKSBEHANDLING
        }
        var harSendtEtVarsel = false
        forstePerArbeidsgiver.forEachIndexed { idx, perioden ->
            val soknaden = perioden.soknader.sortedBy { it.sendt }.last()

            if (harSendtEtVarsel) {
                val now = Instant.now()
                vedtaksperiodeBehandlingStatusRepository.save(
                    VedtaksperiodeBehandlingStatusDbRecord(
                        vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                        opprettetDatabase = now,
                        tidspunkt = now,
                        status = StatusVerdi.VARSLET_FORSINKET_PA_ANNEN_ORGNUMMER,
                        dittSykefravaerMeldingId = null,
                        brukervarselId = null,
                    ),
                )
                vedtaksperiodeBehandlingRepository.save(
                    perioden.vedtaksperiode.copy(
                        sisteVarslingstatus = StatusVerdi.VARSLET_FORSINKET_PA_ANNEN_ORGNUMMER,
                        sisteVarslingstatusTidspunkt = now,
                        oppdatertDatabase = now,
                    ),
                )

                return@forEachIndexed
            }

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
            harSendtEtVarsel = true
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
        }

        return CronJobStatus.SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28
    }
}
