package no.nav.helse.flex.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate
import java.time.LocalDateTime

data class VedtakFattetForEksternDto(
    val fødselsnummer: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalingId: String?,
    val vedtakFattetTidspunkt: LocalDateTime?,
)

fun String.tilVedtakFattetForEksternDto(): VedtakFattetForEksternDto = objectMapper.readValue(this)
