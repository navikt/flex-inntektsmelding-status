package no.nav.helse.flex.inntektsmelding

fun List<InntektsmeldingMedStatus>.overlapper(): Boolean {
    return this.any { a ->
        this.filter { a != it }
            .any { it.overlapper(a) }
    }
}

fun InntektsmeldingMedStatus.overlapper(andre: InntektsmeldingMedStatus) =
    (this.vedtakFom >= andre.vedtakFom && this.vedtakFom <= andre.vedtakTom) ||
        (this.vedtakTom <= andre.vedtakTom && this.vedtakTom >= andre.vedtakFom)
