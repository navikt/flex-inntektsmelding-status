package no.nav.helse.flex.varseltekst

import no.nav.helse.flex.util.norskDateFormat
import java.time.LocalDate

fun skapVenterPåInntektsmelding15Tekst(orgnavn: String): String =
    "Status i saken din om sykepenger: " +
        "Vi venter på inntektsmelding fra $orgnavn."

fun skapVenterPåInntektsmelding28Tekst(orgnavn: String): String =
    "Status i saken din om sykepenger: " +
        "Vi mangler fortsatt inntektsmelding fra $orgnavn og har sendt en påminnelse til arbeidsgiveren din om dette." +
        "Når vi får den kan vi begynne å behandle søknaden din."

fun skapForsinketSaksbehandling56Tekst(): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Status i saken din om sykepenger: Vi beklager! Saksbehandlingen tar dessverre lengre tid" +
        "enn forventet. Vi regner med at saken din vil være ferdigbehandlet i løpet av de neste 4 ukene."
}

fun skapRevarselForsinketSaksbehandlingTekst(): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Status i saken din om sykepenger: Vi beklager! Saksbehandlingen tar dessverre lengre tid enn forventet." +
        "Vi regner med at søknaden din om sykepenger vil være ferdigbehandlet i løpet av de neste 4 ukene."
}

fun skapForelagteOpplysningerTekst(): String =
    "Status i saken din om sykepenger: Vi har hentet opplysninger om inntekten din fra a-ordningen for sykefraværet." +
        "Vi trenger at du sjekker om de stemmer."

internal fun LocalDate.formater(): String? = this.format(norskDateFormat)

const val SAKSBEHANDLINGSTID_URL = "https://www.nav.no/saksbehandlingstider#sykepenger"
