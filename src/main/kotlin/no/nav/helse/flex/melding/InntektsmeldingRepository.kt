package no.nav.helse.flex.melding

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface InntektsmeldingRepository : CrudRepository<Inntektsmelding, String>

data class Inntektsmelding(
    @Id
    val id: String? = null,
    val fnr: String,
    val orgNr: String,
    val orgNavn: String,
    val opprettet: Instant,
    val vedtakFom: LocalDate,
    val vedtakTom: LocalDate,
    val eksternTimestamp: Instant,
    val eksternId: String,
)

enum class MeldingStatus {
    MANGLER,
    MOTTATT,
    BRUKERNOTIFIKSJON_SENDT,
    DITT_SYKEFRAVAER_MELDING_SENDT,
    BRUKERNOTIFIKSJON_LUKKET,
    DITT_SYKEFRAVAER_LUKKET,
    BRUKERNOTIFIKSJON_DONE_SENDT,
    DITT_SYKEFRAVAER_DONE_SENDT,
}
