package no.nav.helse.flex.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate
import java.util.*



import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VedtakRepository : CrudRepository<Vedtak, String> {
    fun findByVedtakUuid(VedtakUuid: String): Vedtak?
}

data class Vedtak(
    @Id
    val id: String? = null,
    val VedtakUuid: String,
    val orgnummer: String?,
    val soknadstype: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val fnr: String,
    val sendt: Instant,
    val opprettetDatabase: Instant,
)
