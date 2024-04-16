package no.nav.helse.flex.util

import no.nav.helse.flex.vedtaksperiode.InntektsmeldingMedStatus
import java.time.LocalDate

fun List<InntektsmeldingMedStatus>.finnSykefraværStart(fom: LocalDate): LocalDate {
    this.find { it.vedtakTom.erRettFør(fom) }?.let { return finnSykefraværStart(it.vedtakFom) }
    return fom
}
