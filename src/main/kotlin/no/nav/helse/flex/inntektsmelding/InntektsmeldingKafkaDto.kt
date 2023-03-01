package no.nav.helse.flex.inntektsmelding

import java.time.LocalDate
import java.time.OffsetDateTime

data class InntektsmeldingKafkaDto(
    val id: String,
    val status: Status,
    val sykmeldt: String,
    val arbeidsgiver: String,
    val vedtaksperiode: Vedtaksperiode,
    val tidspunkt: OffsetDateTime,
)

data class Vedtaksperiode(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

enum class Status {
    MANGLER_INNTEKTSMELDING,
    HAR_INNTEKTSMELDING,
    TRENGER_IKKE_INNTEKTSMELDING,
    BEHANDLES_UTENFOR_SPLEIS,
}

fun Status.tilStatusVerdi(): StatusVerdi {
    return when (this) {
        Status.MANGLER_INNTEKTSMELDING -> StatusVerdi.MANGLER_INNTEKTSMELDING // Venter på inntektsmelding
        Status.HAR_INNTEKTSMELDING -> StatusVerdi.HAR_INNTEKTSMELDING // Inntektsmelding mottatt, eller vedtaksperiode som ikke trenger ny inntektsmelding
        Status.TRENGER_IKKE_INNTEKTSMELDING -> StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING // Ikke utbetaling, innenfor arbeidsgiverperiode
        Status.BEHANDLES_UTENFOR_SPLEIS -> StatusVerdi.BEHANDLES_UTENFOR_SPLEIS // Kastes ut fra speil og behandles i gosys
    }
}
