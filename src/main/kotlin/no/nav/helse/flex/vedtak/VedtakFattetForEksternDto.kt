package no.nav.helse.flex.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate

data class VedtakFattetForEksternDto(
    val fødselsnummer: String,
    // val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    // val vedtaksperiodeId: String, // todo utgår pga at
    // val skjæringstidspunkt: LocalDate,
//    val dokumenter: List<Dokument>,
    //  val inntekt: Double,
    // val sykepengegrunnlag: Double,
//    val grunnlagForSykepengegrunnlag: Double,
//    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>?,
    // ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD og VET_IKKE
//    val begrensning: String?,
//    val utbetalingId: String?,
//    val vedtakFattetTidspunkt: LocalDate?,
//    val sykepengegrunnlagsfakta: JsonNode? = null,
//    val begrunnelser: List<Begrunnelse>? = null,
    // val tags: List<String>? = null,
)

// data class Begrunnelse(
//    val type: String,
//    val begrunnelse: String,
//    val perioder: List<PeriodeImpl>,
// )
//

//
// interface Periode {
//    val fom: LocalDate
//    val tom: LocalDate
//
//    fun overlapper(andre: Periode) =
//        (this.fom >= andre.fom && this.fom <= andre.tom) ||
//            (this.tom <= andre.tom && this.tom >= andre.fom)
// }
//
// class PeriodeImpl(override val fom: LocalDate, override val tom: LocalDate) : Periode

fun String.tilVedtakFattetForEksternDto(): VedtakFattetForEksternDto = objectMapper.readValue(this)
