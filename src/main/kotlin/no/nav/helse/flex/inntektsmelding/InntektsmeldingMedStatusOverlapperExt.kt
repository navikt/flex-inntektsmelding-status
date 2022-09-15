package no.nav.helse.flex.inntektsmelding

fun List<InntektsmeldingMedStatus>.overlapper(): Boolean {
    val perioderViSjekker = this.filter { it.status != StatusVerdi.BEHANDLES_UTENFOR_SPLEIS }
    return perioderViSjekker
        .any { a ->
            perioderViSjekker.filter { a != it }
                .any { it.overlapper(a) }
        }
}

fun List<InntektsmeldingMedStatus>.manglendeInntektsmeldingOverlapperBehandlesUtaforSpleis(): Boolean {
    val behandlesUtenforSpleis = this.filter { it.status == StatusVerdi.BEHANDLES_UTENFOR_SPLEIS }
    val manglendeInntektsmelding = this.filter { it.status == StatusVerdi.MANGLER_INNTEKTSMELDING }
    return behandlesUtenforSpleis
        .any { a ->
            manglendeInntektsmelding.filter { a != it }
                .any { it.overlapper(a) }
        }
}

fun InntektsmeldingMedStatus.overlapper(andre: InntektsmeldingMedStatus) =
    (this.vedtakFom >= andre.vedtakFom && this.vedtakFom <= andre.vedtakTom) ||
        (this.vedtakTom <= andre.vedtakTom && this.vedtakTom >= andre.vedtakFom)
