package no.nav.helse.flex.varseltekst

import no.nav.helse.flex.util.norskDateFormat
import java.time.LocalDate

fun skapVenterPåInntektsmelding15Tekst(
    fom: LocalDate,
    orgnavn: String,
): String {
    val dato = fom.format(norskDateFormat)

    return "Du har gjort din del. Nå venter vi på inntektsmeldingen fra $orgnavn for sykefraværet som startet $dato."
}

fun skapVenterPåInntektsmelding28Tekst(
    fom: LocalDate,
    orgnavn: String,
): String {
    val dato = fom.format(norskDateFormat)

    return "Saksbehandlingen er forsinket fordi vi mangler inntektsmeldingen fra $orgnavn for sykefraværet som startet $dato."
}

fun skapForsinketSaksbehandling28Tekst(): String {
    return "Behandlingen av søknaden din tar lengre tid enn forventet. " +
        "Vi beklager eventuelle ulemper dette medfører. Vi vil normalt behandle saken din innen 4 uker."
}
