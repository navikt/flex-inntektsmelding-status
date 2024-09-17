package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.util.tilLocalDate
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun LocalDate.erMindreEnn30DagerFra(annenDato: LocalDate): Boolean {
    val dagerImellom = ChronoUnit.DAYS.between(this, annenDato).let { Math.abs(it) }
    return dagerImellom < 30
}

fun finnLikesteInntektsmelding(
    inntektsmeldinger: List<InntektsmeldingDbRecord>,
    perioden: FullVedtaksperiodeBehandling,
    soknaden: Sykepengesoknad,
): InntektsmeldingDbRecord? {
    val matchPaVedtaksperiodeId =
        inntektsmeldinger
            .filter { it.vedtaksperiodeId == perioden.vedtaksperiode.vedtaksperiodeId }
            .sortedByDescending { it.mottattDato }
            .firstOrNull()

    if (matchPaVedtaksperiodeId != null) {
        return matchPaVedtaksperiodeId
    }

    val matchPaOrgnrOgLikDato =
        inntektsmeldinger.filter { it.virksomhetsnummer == soknaden.orgnummer }
            .filter {
                (it.foersteFravaersdag ?: it.mottattDato.tilLocalDate()).erMindreEnn30DagerFra(soknaden.startSyketilfelle)
            }
            .sortedByDescending { it.mottattDato }
            .firstOrNull()

    if (matchPaOrgnrOgLikDato != null) {
        return matchPaOrgnrOgLikDato
    }

    return null
}
