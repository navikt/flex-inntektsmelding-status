package no.nav.helse.flex.forelagteopplysningerainntekt

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ForelagteOpplysningerRepository : CrudRepository<ForelagteOpplysningerDbRecord, String> {
    fun existsByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): Boolean

    fun findAllByForelagtIsNull(): List<ForelagteOpplysningerDbRecord>

    fun findAllByForelagtIsNotNull(): List<ForelagteOpplysningerDbRecord>

    fun findByFnr(fnr: String): List<ForelagteOpplysningerDbRecord>

    fun findByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): ForelagteOpplysningerDbRecord?
}
