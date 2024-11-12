package no.nav.helse.flex.varseltekst

import no.nav.helse.flex.util.norskDateFormat
import no.nav.helse.flex.util.tilLocalDate
import java.time.Instant

fun skapVenterPåInntektsmelding15Tekst(
    orgnavn: String,
    sendt: Instant,
): String {
    return "Vi venter på inntektsmelding fra $orgnavn. Når vi får den kan vi behandle søknaden om sykepenger du sendte ${sendt.formater()}."
}

fun Instant.formater(): String? {
    return this.tilLocalDate().format(norskDateFormat)
}

fun skapVenterPåInntektsmelding28Tekst(
    orgnavn: String,
    sendt: Instant,
): String {
    return "Saksbehandlingen for søknaden om sykepenger du sendte ${sendt.formater()} er forsinket fordi " +
        "vi fortsatt venter på inntektsmelding fra $orgnavn."
}

fun skapForsinketSaksbehandling28Tekst(): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Behandlingen av søknaden din om sykepenger tar lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører. Se vår oversikt over forventet saksbehandlingstid."
}

fun skapRevarselForsinketSaksbehandlingTekst(): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Beklager, men behandlingen av søknaden din om sykepenger tar enda lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører."
}

fun skapForelagteOpplysningerTekst(): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Vi har forelagt opplysninger om inntekt for deg. Du kan se opplysningene i sykefraværet ditt." // TODO: add final version of this
}

const val SAKSBEHANDLINGSTID_URL = "https://www.nav.no/saksbehandlingstider#sykepenger"
