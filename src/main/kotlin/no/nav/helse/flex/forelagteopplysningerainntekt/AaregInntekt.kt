package no.nav.helse.flex.forelagteopplysningerainntekt

import java.time.LocalDateTime

data class AaregInntekt(
    val tidsstempel: LocalDateTime,
    val inntekter: List<Inntekt>,
    val omregnetAarsinntekt: Double,
    val orgnavn: String,
) {
    data class Inntekt(
        val maned: String,
        val belop: Double? = null,
    )
}
