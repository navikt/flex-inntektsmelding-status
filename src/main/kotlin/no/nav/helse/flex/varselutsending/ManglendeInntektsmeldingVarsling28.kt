package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.vedtaksperiodebehandling.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ManglendeInntektsmeldingVarsling28(
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
    ): CronJobStatus {
        if (environmentToggles.isProduction()) {
            return CronJobStatus.MANGLENDE_INNTEKTSMELDING_VARSEL_28_DISABLET_I_PROD
        }
        lockRepository.settAdvisoryTransactionLock(fnr)

        val allePerioder = hentAltForPerson.hentAltForPerson(fnr)

        val venterPaaArbeidsgiver =
            allePerioder
                .filter { it.vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_ARBEIDSGIVER }
                .filter { it.vedtaksperiode.sisteVarslingstatus == null }
                .filter { periode -> periode.soknader.all { it.sendt.isBefore(sendtFoer) } }

        if (venterPaaArbeidsgiver.size != 1) {
            return CronJobStatus.FLERE_PERIODER_IKKE_IMPLEMENTERT
        }

        return CronJobStatus.TODO_IMPLEMENT
    }
}
