package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class HentAltForPerson(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun hentAltForPerson(fnr: String): List<FullVedtaksperiodeBehandling> {
        val soknader = sykepengesoknadRepository.findByFnr(fnr)

        val vedtaksperiodeBehandlinger =
            vedtaksperiodeBehandlingRepository.findBySykepengesoknadUuidIn(soknader.map { it.sykepengesoknadUuid })
        val statuser =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(vedtaksperiodeBehandlinger.map { it.id!! })

        return soknader.map { soknad ->
            val vedtaksperiodeBehandlingerMedStatus =
                vedtaksperiodeBehandlinger
                    .filter { it.sykepengesoknadUuid == soknad.sykepengesoknadUuid }
                    .map { VedtaksperiodeMedStatuser(it, statuser.filter { status -> status.vedtaksperiodeBehandlingId == it.id }) }
            FullVedtaksperiodeBehandling(soknad, vedtaksperiodeBehandlingerMedStatus)
        }
    }
}

data class VedtaksperiodeMedStatuser(
    val vedtaksperiode: VedtaksperiodeBehandlingDbRecord,
    val status: List<VedtaksperiodeBehandlingStatusDbRecord>,
)

data class FullVedtaksperiodeBehandling(
    val soknad: Sykepengesoknad,
    val vedtaksperioder: List<VedtaksperiodeMedStatuser>,
)
