package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.springframework.stereotype.Service


@Service
class KombinerDataService(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository
) {

    fun mergeForelagteOpplysningerWithSykepengesoknad(vedtaksperiodeId: String, behandlingId: String): List<KombinerteData> {

        // Fetch forelagte opplysninger
        val opplysninger = forelagteOpplysningerRepository.findByVedtaksperiodeIdAndBehandlingId(vedtaksperiodeId, behandlingId)

        // Fetch vedtaksperiode behandling record
        val behandlingRecord = vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(vedtaksperiodeId, behandlingId)

        if (behandlingRecord != null && opplysninger != null) {
            // Fetch related vedtaksperiode_behandling_sykepengesoknad records
            val behandlingSykepengesoknadRecords = vedtaksperiodeBehandlingSykepengesoknadRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(behandlingRecord.id!!))

            // Collect UUIDs to fetch matching sykepengesoknad records
            val sykepengesoknadUuids = behandlingSykepengesoknadRecords.map { it.sykepengesoknadUuid }
            val sykepengesoknader = sykepengesoknadRepository.findAllById(sykepengesoknadUuids) // Unresolved reference: findByIdIn

            // Map the result
            return sykepengesoknader.map { sykepengesoknad ->
                KombinerteData(
                    opplysninger = opplysninger,
                    behandling = behandlingRecord,
                    sykepengesoknad = sykepengesoknad
                )
            }
        } else {
            return emptyList()
        }
    }
}

data class KombinerteData(
    val opplysninger: ForelagteOpplysningerDbRecord,
    val behandling: VedtaksperiodeBehandlingDbRecord,
    val sykepengesoknad: Sykepengesoknad
)
