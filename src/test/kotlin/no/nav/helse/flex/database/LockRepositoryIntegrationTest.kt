package no.nav.helse.flex.database

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.AnyException
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be in range`
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LockRepositoryIntegrationTest : FellesTestOppsett() {

    @Autowired
    private val transactionManager: PlatformTransactionManager? = null

    private var transactionTemplate: TransactionTemplate? = null

    @BeforeEach
    fun setUp() {
        transactionTemplate = TransactionTemplate(transactionManager!!)
    }

    @Test
    fun `Sjekker at vi er en i transaksjon`() {
        doInTransaction { require(TransactionSynchronizationManager.isActualTransactionActive()) }
    }

    @Test
    fun `Feiler når vi ikke er i en transaksjon`() {
        invoking { lockRepository.settAdvisoryTransactionLock(1) } shouldThrow AnyException
    }

    @Test
    fun `Test at to transaksjoner ikke kan låse samtidig`() {
        val completer = CompletableFuture<Any>()
        val forsteLatch = CountDownLatch(1)
        val andreLatch = CountDownLatch(1)

        lateinit var firstTimestamp: Instant
        lateinit var secondTimestamp: Instant

        thread {
            doInTransaction {
                lockRepository.settAdvisoryTransactionLock(1)
                firstTimestamp = Instant.now()
                forsteLatch.countDown()
                // Venter på completion før transaksjonen avsluttes
                completer.get()
            }
        }

        // Venter til vi vet at den første tråden har startet før vi starter en ny transaksjon.
        forsteLatch.await()

        thread {
            doInTransaction {
                // Låsen i den andre transaksjonen skal ikke bli satt før den første er ferdig.
                lockRepository.settAdvisoryTransactionLock(1)
                secondTimestamp = Instant.now()
                andreLatch.countDown()
            }
        }

        // Venter ett sekund før vi tillater transaksjonen som holder på låsen å avslutte.
        TimeUnit.SECONDS.sleep(1L).let {
            completer.complete(Any())
        }

        andreLatch.await()
        Duration.between(firstTimestamp, secondTimestamp).toMillis() `should be in range` 1000L..1100L
    }

    private fun doInTransaction(function: () -> Unit) {
        transactionTemplate!!.execute {
            function()
        }
    }
}
