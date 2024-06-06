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
import no.nav.helse.flex.varseltekst.skapVenterPåInntektsmeldingTekst
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random

@Component
class ManglendeInntektsmeldingVarsling(
    private val hentAltForPerson: HentAltForPerson,
    private val lockRepository: LockRepository,
    private val environmentToggles: EnvironmentToggles,
    private val brukervarsel: Brukervarsel,
    private val organisasjonRepository: OrganisasjonRepository,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserManglendeInntektsmeldingKandidat(
        fnr: String,
        sendtFoer: Instant,
    ): String {
        if (environmentToggles.isProduction()) {
            return "manglende inntektsmelding kandidat togglet av i prod"
        }
        lockRepository.settAdvisoryTransactionLock(fnr)

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val venterPaaArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }
                .filter { it.vedtaksperiode.sisteVarslingstatus == null }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }

        if (venterPaaArbeidsgiver.size != 1) {
            return "varsler for flere perioder ikke implementert"
        }
        // TODO sjekk om vi nettop har sendt noe annet?

        val soknaden = venterPaaArbeidsgiver.first().soknader.sortedBy { it.sendt }.last()
        val perioden = venterPaaArbeidsgiver.first()

        val seedUUID = UUID.fromString(perioden.statuser.first { it.status == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }.id)

        val randomGenerator = Random(seedUUID.mostSignificantBits xor seedUUID.leastSignificantBits)

        fun nextUUID(): UUID {
            val mostSigBits = randomGenerator.nextLong()
            val leastSigBits = randomGenerator.nextLong()
            return UUID(mostSigBits, leastSigBits)
        }

        val brukervarslId = nextUUID().toString()

        val orgnavn = organisasjonRepository.findByOrgnummer(soknaden.orgnummer!!)?.navn ?: soknaden.orgnummer

        val synligFremTil = OffsetDateTime.now().plusMonths(4).toInstant()
        brukervarsel.beskjedManglerInntektsmelding(
            fnr = fnr,
            bestillingId = brukervarslId,
            orgNavn = orgnavn,
            fom = soknaden.startSyketilfelle,
            synligFremTil = synligFremTil,
        )

        val meldingBestillingId = nextUUID().toString()
        meldingKafkaProducer.produserMelding(
            meldingUuid = meldingBestillingId,
            meldingKafkaDto =
                MeldingKafkaDto(
                    fnr = fnr,
                    opprettMelding =
                        OpprettMelding(
                            tekst = skapVenterPåInntektsmeldingTekst(soknaden.startSyketilfelle, orgnavn),
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
                status = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING,
                brukervarselId = brukervarslId,
                dittSykefravaerMeldingId = meldingBestillingId,
            ),
        )

        vedtaksperiodeBehandlingRepository.save(
            perioden.vedtaksperiode.copy(
                sisteVarslingstatus = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING,
                sisteVarslingstatusTidspunkt = Instant.now(),
                oppdatertDatabase = Instant.now(),
            ),
        )

        return "VARSLET_MANGLER_INNTEKTSMELDING"
    }
}
