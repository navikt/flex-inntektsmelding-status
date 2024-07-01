package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling

fun List<InntektsmeldingDbRecord>.matchInntektsmeldingMedPeriode(perioden: FullVedtaksperiodeBehandling): InntektsmeldingDbRecord? {
    return this
        .filter { it.vedtaksperiodeId == perioden.vedtaksperiode.vedtaksperiodeId }
        .firstOrNull()
}
