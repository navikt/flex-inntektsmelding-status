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
import no.nav.helse.flex.varseltekst.skapVenterPåInntektsmelding28Tekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

@Component
class ManglendeInntektsmeldingVarsling28(
    private val hentAltForPerson: HentAltForPerson,
    private val lockRepository: LockRepository,
    private val environmentToggles: EnvironmentToggles,
    private val meldingOgBrukervarselDone: MeldingOgBrukervarselDone,
    private val brukervarsel: Brukervarsel,
    private val organisasjonRepository: OrganisasjonRepository,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
    @Value("\${MINIMUMSTID_MELLOM_FORSTE_OG_ANDRE_MANGLER_IM_VARSEL}") private val minimumstid: String,
) {
    private val log = logger()
    val duration = Duration.parse(minimumstid)

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserManglendeInntektsmelding28(
        fnr: String,
        sendtFoer: Instant,
    ): CronJobStatus {
        if (environmentToggles.isProduction()) {
            return CronJobStatus.MANGLENDE_INNTEKTSMELDING_VARSEL_28_DISABLET_I_PROD
        }
        lockRepository.settAdvisoryTransactionLock(fnr)

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val venterPaaArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }
                .filter { it.vedtaksperiode.sisteVarslingstatus == StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_15 }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }

        if (venterPaaArbeidsgiver.isEmpty()) {
            return CronJobStatus.INGEN_PERIODE_FUNNET_FOR_VARSEL_MANGLER_INNTEKTSMELDING_18
        }

        val harFattVarselNylig =
            venterPaaArbeidsgiver
                .mapNotNull { it.vedtaksperiode.sisteVarslingstatusTidspunkt }
                .any { it.isAfter(Instant.now().minus(duration)) }

        if (harFattVarselNylig) {
            return CronJobStatus.HAR_FATT_NYLIG_VARSEL
        }

        venterPaaArbeidsgiver.forEachIndexed { idx, perioden ->

            val soknaden = perioden.soknader.sortedBy { it.sendt }.last()

            meldingOgBrukervarselDone.doneSendteManglerImVarsler(perioden.vedtaksperiode, fnr)

            val randomGenerator =
                SeededUuid(perioden.statuser.first { it.status == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }.id!!, 2)

            val brukervarselId = randomGenerator.nextUUID()

            val orgnavn = organisasjonRepository.findByOrgnummer(soknaden.orgnummer!!)?.navn ?: soknaden.orgnummer

            val synligFremTil = OffsetDateTime.now().plusMonths(4).toInstant()
            brukervarsel.beskjedManglerInntektsmelding(
                fnr = fnr,
                bestillingId = brukervarselId,
                orgNavn = orgnavn,
                fom = soknaden.startSyketilfelle,
                synligFremTil = synligFremTil,
                forsinketSaksbehandling = true,
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
                                tekst = skapVenterPåInntektsmelding28Tekst(orgnavn),
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
                    vedtaksperiodeBehandlingId = perioden.vedtaksperiode.id!!,
                    opprettetDatabase = Instant.now(),
                    tidspunkt = Instant.now(),
                    status = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_28,
                    brukervarselId = brukervarselId,
                    dittSykefravaerMeldingId = meldingBestillingId,
                ),
            )

            vedtaksperiodeBehandlingRepository.save(
                perioden.vedtaksperiode.copy(
                    sisteVarslingstatus = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_28,
                    sisteVarslingstatusTidspunkt = Instant.now(),
                    oppdatertDatabase = Instant.now(),
                ),
            )
        }

        return CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_28
    }
}
