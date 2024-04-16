package no.nav.helse.flex.util

import no.nav.helse.flex.vedtaksperiode.VedtaksperiodeMedStatus
import java.time.LocalDate

fun List<VedtaksperiodeMedStatus>.finnSykefraværStart(fom: LocalDate): LocalDate {
    this.find { it.vedtakTom.erRettFør(fom) }?.let { return finnSykefraværStart(it.vedtakFom) }
    return fom
}
