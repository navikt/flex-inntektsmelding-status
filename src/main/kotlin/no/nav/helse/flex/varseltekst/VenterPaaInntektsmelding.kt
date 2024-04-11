package no.nav.helse.flex.varseltekst

import no.nav.helse.flex.util.norskDateFormat
import java.time.LocalDate

fun skapVenterPåInntektsmeldingTekst(
    fom: LocalDate,
    orgnavn: String,
): String {
    val dato = fom.format(norskDateFormat)

    return "Du har gjort din del. Nå venter vi på inntektsmeldingen fra $orgnavn for sykefraværet som startet $dato."
}
