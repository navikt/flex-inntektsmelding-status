package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerRepository
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.springframework.stereotype.Component

@Component
class HentAlleForelagteOpplysningerForPerson(
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
) {
    fun hentAlleForelagteOpplysningerFor(fnr: String): List<ForelagteOpplysningerDbRecord> {
        val sykepengesoknader =
            sykepengesoknadRepository.findByFnr(fnr)
        val sykepengesoknadUuids = sykepengesoknader.map { it.sykepengesoknadUuid }
        val relasjoner =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(sykepengesoknadUuids)
        val vedtaksperiodeBehandlinger =
            relasjoner.map {
                vedtaksperiodeBehandlingRepository.findById(it.vedtaksperiodeBehandlingId).get()
            }
        return vedtaksperiodeBehandlinger.flatMap {
            forelagteOpplysningerRepository.findAllByVedtaksperiodeIdAndBehandlingId(
                it.vedtaksperiodeId,
                it.behandlingId,
            )
        }
    }
}
