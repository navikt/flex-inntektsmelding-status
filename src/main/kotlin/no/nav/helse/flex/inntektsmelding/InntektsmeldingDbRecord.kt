package no.nav.helse.flex.inntektsmelding

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate

@Table("inntektsmelding")
data class InntektsmeldingDbRecord(
    @Id
    val id: String? = null,
    val inntektsmeldingId: String,
    val fnr: String,
    val arbeidsgivertype: String,
    val virksomhetsnummer: String?,
    val fullRefusjon: Boolean,
    val opprettet: Instant,
    val mottattDato: Instant,
    val foersteFravaersdag: LocalDate?,
    val vedtaksperiodeId: String?,
)
