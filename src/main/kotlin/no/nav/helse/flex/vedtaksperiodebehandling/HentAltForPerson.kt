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
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
) {
//    @Transactional(propagation = Propagation.REQUIRED)
//    fun hentAltForPerson(fnr: String): List<FullVedtaksperiodeBehandling> {
//        val soknader = sykepengesoknadRepository.findByFnr(fnr)
//
//        val vedtaksperiodeBehandlinger =
//            vedtaksperiodeBehandlingRepository.findBySykepengesoknadUuidIn(soknader.map { it.sykepengesoknadUuid })
//        val statuser =
//            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(vedtaksperiodeBehandlinger.map { it.id!! })
//
//        return soknader.map { soknad ->
//            val vedtaksperiodeBehandlingerMedStatus =
//                vedtaksperiodeBehandlinger
//                    // .filter { it.sykepengesoknadUuid == soknad.sykepengesoknadUuid } // todo her må vi gjøre noe, men hva? sende inn en liste
//                    .any { it.vedtaksperiodeId == soknad.sykepengesoknadUuid } // todo her må vi gjøre noe, men hva? sende inn en liste
//                    .map { VedtaksperiodeMedStatuser(it, statuser.filter { status -> status.vedtaksperiodeBehandlingId == it.id }) }
//            FullVedtaksperiodeBehandling(soknad, vedtaksperiodeBehandlingerMedStatus)
//        }
//    }

      @Transactional(propagation = Propagation.REQUIRED)
    fun hentAltForPerson(fnr: String): List<FullVedtaksperiodeBehandling> {
        val soknader = sykepengesoknadRepository.findByFnr(fnr)
        val soknadUuids = soknader.map { it.sykepengesoknadUuid }

        // Finn vedtaksperiodeBehandlinger basert på sykepengesoknadUuid
        val behandlingSoknadRecords = vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingIdIn(soknadUuids)
        val behandlingIds = behandlingSoknadRecords.map { it.vedtaksperiodeBehandlingId }

        // Finn vedtaksperiodeBehandlinger
        val vedtaksperiodeBehandlinger = vedtaksperiodeBehandlingRepository.findAllById(behandlingIds)

        // Finn statuser for behandlingene
        val statuser = vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(behandlingIds)



        return soknader.map { soknad ->
            val behandlingerForSoknad = behandlingSoknadRecords.filter { it.sykepengesoknadUuid == soknad.sykepengesoknadUuid }
            val vedtaksperiodeBehandlingerMedStatus = behandlingerForSoknad.mapNotNull { behandlingSoknad ->
                val behandling = vedtaksperiodeBehandlinger.find { it.id == behandlingSoknad.vedtaksperiodeBehandlingId }
                if (behandling != null) {
                    VedtaksperiodeMedStatuser(behandling, statuser.filter { status -> status.vedtaksperiodeBehandlingId == behandling.id })
                } else null
            }
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
