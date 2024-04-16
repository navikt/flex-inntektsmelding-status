package no.nav.helse.flex.inntektsmelding

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface InntektsmeldingRepository : CrudRepository<InntektsmeldingDbRecord, String> {
    fun existsByEksternId(eksternId: String): Boolean

    fun findInntektsmeldingDbRecordByEksternId(eksternId: String): InntektsmeldingDbRecord?
}

@Table("inntektsmelding")
data class InntektsmeldingDbRecord(
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
    val ferdigBehandlet: Instant? = null,
)
