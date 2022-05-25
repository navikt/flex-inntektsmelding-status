package no.nav.helse.flex.inntektsmelding

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface InntektsmeldingStatusRepository : CrudRepository<InntektsmeldingStatusDbRecord, String>

@Table("inntektsmelding_status")
data class InntektsmeldingStatusDbRecord(
    @Id
    val id: String? = null,
    val inntektsmeldingId: String,
    val opprettet: Instant,
    val status: InntektsmeldingStatus,
)

enum class InntektsmeldingStatus {
    MANGLER,
    MOTTATT,
    BRUKERNOTIFIKSJON_SENDT,
    DITT_SYKEFRAVAER_MELDING_SENDT,
    BRUKERNOTIFIKSJON_LUKKET,
    DITT_SYKEFRAVAER_LUKKET,
    BRUKERNOTIFIKSJON_DONE_SENDT,
    DITT_SYKEFRAVAER_DONE_SENDT,
}
