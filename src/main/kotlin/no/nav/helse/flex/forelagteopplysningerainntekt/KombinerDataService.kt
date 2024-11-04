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

        val opplysninger = forelagteOpplysningerRepository.findByVedtaksperiodeIdAndBehandlingId(vedtaksperiodeId, behandlingId)

        val behandlingRecord = vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(vedtaksperiodeId, behandlingId)



        if (behandlingRecord != null && opplysninger != null) {

            // finn id fra vedtaksperiode_behandling  ved å søke på behandlingsid og vedtaksperiode_id
            val behandlingSykepengesoknadRecords = vedtaksperiodeBehandlingSykepengesoknadRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(behandlingRecord.id!!))

            val sykepengesoknadUuids = behandlingSykepengesoknadRecords.map { it.sykepengesoknadUuid }
            val sykepengesoknader = sykepengesoknadRepository.findAllById(sykepengesoknadUuids) // Unresolved reference: findByIdIn

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
