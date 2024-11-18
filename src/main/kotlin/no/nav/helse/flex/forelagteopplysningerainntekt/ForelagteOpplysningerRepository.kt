package no.nav.helse.flex.forelagteopplysningerainntekt

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ForelagteOpplysningerRepository : CrudRepository<ForelagteOpplysningerDbRecord, String> {
    fun existsByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): Boolean

    fun findAllByForelagtIsNull(): List<ForelagteOpplysningerDbRecord>

    fun findByFnrAndForelagtGreaterThan(
        fnr: String,
        tidspunkt: Instant,
    ): List<ForelagteOpplysningerDbRecord>

    fun findAllByFnr(fnr: String): List<ForelagteOpplysningerDbRecord>

    fun findAllByVedtaksperiodeIdAndBehandlingId(vedtaksperiodeId: String, behandlingId: String): List<ForelagteOpplysningerDbRecord>
}
