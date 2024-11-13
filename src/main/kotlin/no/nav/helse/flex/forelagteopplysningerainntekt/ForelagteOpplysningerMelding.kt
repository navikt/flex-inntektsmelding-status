import java.time.LocalDateTime
import java.time.YearMonth

data class ForelagteOpplysningerMelding(
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val tidsstempel: LocalDateTime,
    val omregnetÅrsinntekt: Double,
    val skatteinntekter: List<Skatteinntekt>,
) {
    data class Skatteinntekt(val måned: YearMonth, val beløp: Double)
}
