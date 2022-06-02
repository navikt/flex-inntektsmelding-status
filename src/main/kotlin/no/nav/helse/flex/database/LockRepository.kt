package no.nav.helse.flex.database

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LockRepository(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun settAdvisoryTransactionLock(key: Long) {
        namedParameterJdbcTemplate.update(
            "SELECT pg_advisory_xact_lock(:key)",
            MapSqlParameterSource("key", key)
        )
    }
}
