package no.nav.helse.flex.database

import no.nav.helse.flex.FellesTestOppsett
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

class LockRepositoryIntegrationTest : FellesTestOppsett() {

    @Autowired
    private val transactionManager: PlatformTransactionManager? = null

    private var transactionTemplate: TransactionTemplate? = null

    @BeforeEach
    fun setUp() {
        transactionTemplate = TransactionTemplate(transactionManager!!)
    }

    @Test
    fun failWhenNotInTransaction() {
        doInTransaction { require(TransactionSynchronizationManager.isActualTransactionActive()) }
    }

    private fun doInTransaction(function: () -> Unit) {
        transactionTemplate!!.execute {
            function()
        }
    }
}
