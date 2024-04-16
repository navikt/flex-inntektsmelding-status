package no.nav.helse.flex.vedtaksperiode

fun List<VedtaksperiodeMedStatus>.overlapper(): Boolean {
    val perioderViSjekker = this.filter { it.status != StatusVerdi.BEHANDLES_UTENFOR_SPLEIS }
    return perioderViSjekker
        .any { a ->
            perioderViSjekker.filter { a != it }
                .any { it.overlapper(a) }
        }
}

fun List<VedtaksperiodeMedStatus>.manglendeInntektsmeldingOverlapperBehandlesUtaforSpleis(): Boolean {
    val behandlesUtenforSpleis = this.filter { it.status == StatusVerdi.BEHANDLES_UTENFOR_SPLEIS }
    val manglendeInntektsmelding = this.filter { it.status == StatusVerdi.MANGLER_INNTEKTSMELDING }
    return behandlesUtenforSpleis
        .any { a ->
            manglendeInntektsmelding.filter { a != it }
                .any { it.overlapper(a) }
        }
}

fun VedtaksperiodeMedStatus.overlapper(andre: VedtaksperiodeMedStatus) =
    (this.vedtakFom >= andre.vedtakFom && this.vedtakFom <= andre.vedtakTom) ||
        (this.vedtakTom <= andre.vedtakTom && this.vedtakTom >= andre.vedtakFom)
