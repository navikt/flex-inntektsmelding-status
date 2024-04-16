package no.nav.helse.flex.vedtaksperiode

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface InntektsmeldingStatusRepository : CrudRepository<InntektsmeldingStatusDbRecord, String> {
    fun existsByInntektsmeldingId(inntektsmeldingId: String): Boolean
}

@Table("inntektsmelding_status")
data class InntektsmeldingStatusDbRecord(
    @Id
    val id: String? = null,
    val inntektsmeldingId: String,
    val opprettet: Instant,
    val status: StatusVerdi,
)

enum class StatusVerdi {
    MANGLER_INNTEKTSMELDING,
    HAR_INNTEKTSMELDING,
    TRENGER_IKKE_INNTEKTSMELDING,
    BEHANDLES_UTENFOR_SPLEIS,
    HAR_PERIODE_RETT_FOER,
    BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
    BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
    DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
    DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT,
    DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT,
    DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_LUKKET,
    DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_DONE_SENDT,
    OVELAPPER_BEHANDLES_UTAFOR_SPLEIS_SENDER_IKKE_UT,
    OVELAPPER_SENDER_IKKE_UT,
    VEDTAK_FATTET,
}
