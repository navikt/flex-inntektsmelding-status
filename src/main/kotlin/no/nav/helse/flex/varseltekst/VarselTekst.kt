package no.nav.helse.flex.varseltekst

import no.nav.helse.flex.util.norskDateFormat
import java.time.LocalDate

fun skapVenterPåInntektsmelding15Tekst(
    orgnavn: String,
    startSyketilfelle: LocalDate,
): String =
    "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "Vi venter på inntektsmelding fra $orgnavn."

fun skapVenterPåInntektsmelding28Tekst(
    orgnavn: String,
    startSyketilfelle: LocalDate,
): String =
    "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "Saksbehandlingen er forsinket fordi vi fortsatt venter på inntektsmelding fra $orgnavn."

fun skapForsinketSaksbehandling28Tekst(startSyketilfelle: LocalDate): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "Behandlingen av søknaden din om sykepenger tar lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører. Se vår oversikt over forventet saksbehandlingstid."
}

fun skapRevarselForsinketSaksbehandlingTekst(startSyketilfelle: LocalDate): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "Saksbehandling tar lengre tid enn forventet. Søknaden vil forhåpentligvis være ferdigbehandlet innen 4 uker. Vi beklager eventuelle ulemper dette medfører."
}

fun skapForelagteOpplysningerTekst(startSyketilfelle: LocalDate): String =
    "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "Vi har hentet opplysninger om inntekten din fra a-ordningen. Vi trenger at du sjekker om de stemmer."

internal fun LocalDate.formater(): String? = this.format(norskDateFormat)

const val SAKSBEHANDLINGSTID_URL = "https://www.nav.no/saksbehandlingstider#sykepenger"
