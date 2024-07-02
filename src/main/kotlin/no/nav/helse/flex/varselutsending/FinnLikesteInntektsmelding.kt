package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import java.time.LocalDate
import java.time.temporal.ChronoUnit


fun LocalDate?.isWithin30DaysOf(annenDato: LocalDate?): Boolean {
    val dagerImellom = ChronoUnit.DAYS.between(this, annenDato).let { Math.abs(it) }
    return dagerImellom < 30
}

fun inntektsmeldingSimilar(inntektsmeldinger: List<InntektsmeldingDbRecord>, perioden : FullVedtaksperiodeBehandling, soknaden : Sykepengesoknad) : InntektsmeldingDbRecord? {
      val matchPaVedtaksperiodeId = inntektsmeldinger.filter { it.vedtaksperiodeId == perioden.vedtaksperiode.vedtaksperiodeId }
        .firstOrNull()

    if (matchPaVedtaksperiodeId != null) {
        return matchPaVedtaksperiodeId
    }

    val matchPaOrgnrOgLikDato = inntektsmeldinger.filter { it.virksomhetsnummer == soknaden.orgnummer }
        .filter { it.foersteFravaersdag.isWithin30DaysOf(soknaden.fom) }
        .sortedByDescending { it.mottattDato }
        .firstOrNull()

    if (matchPaOrgnrOgLikDato != null) {
        return matchPaOrgnrOgLikDato
    }

    return null
}
