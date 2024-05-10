package no.nav.helse.flex.database

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Repository
class LockRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryTransactionLock(key: Long) {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock($key)")
    }

    @Transactional(propagation = Propagation.REQUIRED)
    fun settAdvisoryTransactionLock(key: String) {
        val hash = key.stringToLongHashSHA256()
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock($hash)")
    }
}

fun String.stringToLongHashSHA256(): Long {
    val bytes = this.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes) // Bruker SHA-256 til å hash'e input
    var hash = 0L
    // Bruker de første 8 bytene av hashen til å lage et Long-tall
    for (i in 0..7) {
        hash = (hash shl 8) + (digest[i].toInt() and 0xff)
    }
    return hash
}
