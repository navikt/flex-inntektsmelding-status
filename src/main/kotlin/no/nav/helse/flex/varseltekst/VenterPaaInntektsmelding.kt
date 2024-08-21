package no.nav.helse.flex.varseltekst

import no.nav.helse.flex.util.norskDateFormat
import java.time.LocalDate

fun skapVenterPåInntektsmelding15Tekst(
    fom: LocalDate,
    orgnavn: String,
): String {
    val dato = fom.format(norskDateFormat)

    return "Vi venter på inntektsmeldingen fra $orgnavn for sykefraværet som startet $dato."
}

fun skapVenterPåInntektsmelding28Tekst(orgnavn: String): String {
    return "Saksbehandlingen er forsinket fordi vi fortsatt mangler inntektsmelding fra $orgnavn."
}

fun skapForsinketSaksbehandling28Tekst(): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Behandlingen av søknaden din om sykepenger tar lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører. Se vår oversikt over forventet saksbehandlingstid."
}

fun skapRevarselForsinketSaksbehandlingTekst(): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Beklager, men behandlingen av søknaden din om sykepenger tar enda lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører."
}

const val SAKSBEHANDLINGSTID_URL = "https://www.nav.no/saksbehandlingstider#sykepenger"
