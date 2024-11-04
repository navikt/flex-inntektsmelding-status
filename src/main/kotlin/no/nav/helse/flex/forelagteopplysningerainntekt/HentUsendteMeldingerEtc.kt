package no.nav.helse.flex.forelagteopplysningerainntekt


import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.springframework.stereotype.Component

@Component
class HentUsendteMeldingerEtc(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {

    val usendteMeldingerEtc: List<ForelagteOpplysningerDbRecord>
        get() = forelagteOpplysningerRepository.findAllByForelagtIsNull()


    val forsteFnr = usendteMeldingerEtc.map { it.fnr }.firstOrNull()


    // bruk fnr til å finne sykepengesoknader


    /*

    @Table(value = "vedtaksperiode_behandling_sykepengesoknad")
public final data class VedtaksperiodeBehandlingSykepengesoknadDbRecord(
    val id: String? = null,
    val vedtaksperiodeBehandlingId: String,
    val sykepengesoknadUuid: String
)

     */
    val vedtaksperiodeBehandlinger: List<VedtaksperiodeBehandlingSykepengesoknadDbRecord> get() = vedtaksperiodeBehandlingSykepengesoknadRepository.findByFnrIn(forsteFnr!!)


    // sykmeldt fra to jobber, er det da man får et vedtak som inneholder to arbeidsgivere, eller to? jeg tror det er to

}
