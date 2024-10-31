package no.nav.helse.flex.forelagteopplysningerainntekt

import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*


data class ForelagteOpplysningerMelding(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val tidsstempel: LocalDateTime,
    val omregnetÅrsinntekt: Double,
    val skatteinntekter: List<Skatteinntekt>
) {
    data class Skatteinntekt(val måned: YearMonth, val beløp: Double) {}
}
