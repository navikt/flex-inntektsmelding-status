package no.nav.helse.flex.vedtaksperiode

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface VedtaksperiodeRepository : CrudRepository<VedtaksperiodeDbRecord, String> {
    fun existsByEksternId(eksternId: String): Boolean

    fun findVedtaksperiodeDbRecordByEksternId(eksternId: String): VedtaksperiodeDbRecord?

    fun findVedtaksperiodeDbRecordByFnr(fnr: String): List<VedtaksperiodeDbRecord>
}

@Table("vedtaksperiode")
data class VedtaksperiodeDbRecord(
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
