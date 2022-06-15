package no.nav.helse.flex.inntektsmelding

import java.time.LocalDate
import java.util.*

data class InntektsmeldingKafkaDto(
    val uuid: UUID,
    val fnr: Long,
    val orgnummer: String,
    val vedtaksperiodeFom: LocalDate,
    val vedtaksperiodeTom: LocalDate,
    val hendelse: Hendelse,
)

enum class Hendelse {
    INNTEKTSMELDING_MANGLER,
    INNTEKTSMELDING_MOTTATT,
    VEDTAKSPERIODE_FORKASTET,
}
