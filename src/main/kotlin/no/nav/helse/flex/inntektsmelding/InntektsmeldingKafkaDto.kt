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
