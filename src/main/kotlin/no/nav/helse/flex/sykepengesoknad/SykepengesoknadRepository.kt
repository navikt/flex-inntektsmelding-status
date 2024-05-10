package no.nav.helse.flex.sykepengesoknad

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface SykepengesoknadRepository : CrudRepository<Sykepengesoknad, String> {
    fun findBySykepengesoknadUuid(sykepengesoknadUuid: String): Sykepengesoknad?
}

data class Sykepengesoknad(
    @Id
    val id: String? = null,
    val sykepengesoknadUuid: String,
    val orgnummer: String?,
    val soknadstype: String,
    val startSyketilfelle: LocalDate,
    val fom: LocalDate,
    val tom: LocalDate,
    val fnr: String,
    val sendt: Instant,
    val opprettetDatabase: Instant,
)
