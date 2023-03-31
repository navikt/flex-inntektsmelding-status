package no.nav.helse.flex.database

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LockRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryTransactionLock(key: Long) {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock($key)")
    }
}
