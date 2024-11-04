package no.nav.helse.flex.forelagteopplysningerainntekt

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*


@Repository
interface ForelagteOpplysningerRepository : CrudRepository<ForelagteOpplysningerDbRecord, String> {
    fun existsByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): Boolean


    fun findAllByForelagtIsNull(): List<ForelagteOpplysningerDbRecord>

    fun findByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String
    ): ForelagteOpplysningerDbRecord?

}





data class ForelagteOpplysningerMelding(
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val tidsstempel: LocalDateTime,
    val omregnetÅrsinntekt: Double,
    val skatteinntekter: List<Skatteinntekt>,
) {
    data class Skatteinntekt(val måned: YearMonth, val beløp: Double)
}
