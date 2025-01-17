package no.nav.helse.flex.varseltekst

import no.nav.helse.flex.util.norskDateFormat
import java.time.LocalDate

fun skapVenterPåInntektsmelding15Tekst(
    orgnavn: String,
    startSyketilfelle: LocalDate,
): String {
    return "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "\nVi venter på inntektsmelding fra $orgnavn."
}

fun skapVenterPåInntektsmelding28Tekst(
    orgnavn: String,
    startSyketilfelle: LocalDate,
): String {
    return "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "\nSaksbehandlingen er forsinket fordi vi fortsatt venter på inntektsmelding fra ${orgnavn}."
}

fun skapForsinketSaksbehandling28Tekst(
    startSyketilfelle: LocalDate,
): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "\nBehandlingen av søknaden din om sykepenger tar lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører. Se vår oversikt over forventet saksbehandlingstid."
}

fun skapRevarselForsinketSaksbehandlingTekst(
    startSyketilfelle: LocalDate,
): String {
    @Suppress("ktlint:standard:max-line-length")
    return "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "\nSaksbehandling tar lengre tid enn forventet. Søknaden vil forhåpentligvis være ferdigbehandlet innen 4 uker. Vi beklager eventuelle ulemper dette medfører."
}

fun skapForelagteOpplysningerTekst(startSyketilfelle: LocalDate): String {
    return "Status for sykefraværet som startet ${startSyketilfelle.formater()}:" +
        "Vi har hentet opplysninger om inntekten din fra a-ordningen. Vi trenger at du sjekker om de stemmer."
}

internal fun LocalDate.formater(): String? {
    return this.format(norskDateFormat)
}

const val SAKSBEHANDLINGSTID_URL = "https://www.nav.no/saksbehandlingstider#sykepenger"
