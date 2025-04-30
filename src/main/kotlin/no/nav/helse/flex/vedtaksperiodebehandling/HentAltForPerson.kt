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
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun hentAltForPerson(fnr: String): List<FullVedtaksperiodeBehandling> {
        val soknader = sykepengesoknadRepository.findByFnr(fnr)

        val vedtaksperiodeBehandlingerSykepengesoknad: List<VedtaksperiodeBehandlingSykepengesoknadDbRecord> =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(
                soknader.map {
                    it.sykepengesoknadUuid
                },
            )

        val vedtaksperiodeBehandlinger: List<VedtaksperiodeBehandlingDbRecord> =
            vedtaksperiodeBehandlingRepository.findByIdIn(
                vedtaksperiodeBehandlingerSykepengesoknad.map {
                    it.vedtaksperiodeBehandlingId
                },
            )

        val statuser =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(
                vedtaksperiodeBehandlinger.map {
                    it.id!!
                },
            )

        return vedtaksperiodeBehandlinger.map { vb ->

            val soknadIdErForBehandling =
                vedtaksperiodeBehandlingerSykepengesoknad
                    .filter {
                        it.vedtaksperiodeBehandlingId == vb.id
                    }.map { it.sykepengesoknadUuid }
            val soknaderForBehandling = soknader.filter { soknadIdErForBehandling.contains(it.sykepengesoknadUuid) }
            FullVedtaksperiodeBehandling(
                vb,
                soknaderForBehandling,
                statuser.filter { it.vedtaksperiodeBehandlingId == vb.id },
            )
        }
    }

    fun hentAltForVedtaksperiode(vedtaksperiodId: String): List<FullVedtaksperiodeBehandling> {
        val perioder = vedtaksperiodeBehandlingRepository.findByVedtaksperiodeId(vedtaksperiodId)
        val relasjoner =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingIdIn(
                perioder.map { it.id!! },
            )
        val soknader =
            sykepengesoknadRepository.findBySykepengesoknadUuidIn(
                relasjoner.map { it.sykepengesoknadUuid },
            )
        soknader.firstOrNull()?.let {
            return hentAltForPerson(it.fnr)
        }
        return emptyList()
    }
}

data class FullVedtaksperiodeBehandling(
    val vedtaksperiode: VedtaksperiodeBehandlingDbRecord,
    val soknader: List<Sykepengesoknad>,
    val statuser: List<VedtaksperiodeBehandlingStatusDbRecord>,
)
