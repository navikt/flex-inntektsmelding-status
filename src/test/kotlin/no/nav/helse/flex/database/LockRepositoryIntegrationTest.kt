package no.nav.helse.flex.database

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be in range`
import org.amshove.kluent.`should be less than`
import org.awaitility.Awaitility.await
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

private const val FORSINKELSE = 1000L

class LockRepositoryIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        transactionTemplate = TransactionTemplate(transactionManager)
    }

    @Test
    fun `Sjekk at kallet foregår i en i transaksjon`() {
        doInTransaction { require(TransactionSynchronizationManager.isActualTransactionActive()) }
    }

    @Test
    fun `Test at to transaksjoner med samme key låser riktig`() {
        val completer = CompletableFuture<Any>()
        val countDownLatch = CountDownLatch(2)

        lateinit var forsteTimestamp: Instant
        lateinit var andreTimestamp: Instant

        thread {
            doInTransaction {
                lockRepository.settAdvisoryTransactionLock(1)
                forsteTimestamp = Instant.now()
                countDownLatch.countDown()
                // Venter på completion før transaksjonen avsluttes
                completer.get()
            }
        }

        // Venter til vi vet at den første tråden har startet før vi starter en ny transaksjon.
        await().atMost(1L, TimeUnit.SECONDS).until { countDownLatch.count == 1L }

        thread {
            doInTransaction {
                // Låsen i den andre transaksjonen skal ikke bli satt før den første er ferdig.
                lockRepository.settAdvisoryTransactionLock(1)
                andreTimestamp = Instant.now()
                countDownLatch.countDown()
            }
        }

        // Venter før vi tillater transaksjonen som holder på låsen å avslutte.
        TimeUnit.MILLISECONDS.sleep(FORSINKELSE).let {
            completer.complete(Any())
        }

        val completed = countDownLatch.await(1L, TimeUnit.SECONDS)
        completed `should be` true

        Duration.between(forsteTimestamp, andreTimestamp)
            .toMillis() `should be in range` FORSINKELSE..FORSINKELSE + 200L
    }

    @Test
    fun `Test at to transaksjoner med forskjellig key ikke låser hverandre`() {
        val completer = CompletableFuture<Any>()
        val countDownLatch = CountDownLatch(2)

        lateinit var forsteTimestamp: Instant
        lateinit var andreTimestamp: Instant

        thread {
            doInTransaction {
                lockRepository.settAdvisoryTransactionLock(2)
                forsteTimestamp = Instant.now()
                countDownLatch.countDown()
                // Venter på completion før transaksjonen avsluttes
                completer.get()
            }
        }

        // Venter til vi vet at den første tråden har startet før vi starter en ny transaksjon.
        await().atMost(1L, TimeUnit.SECONDS).until { countDownLatch.count == 1L }

        thread {
            doInTransaction {
                // Låsen i den andre transaksjonen skal ikke bli satt før den første er ferdig.
                lockRepository.settAdvisoryTransactionLock(1)
                andreTimestamp = Instant.now()
                countDownLatch.countDown()
            }
        }

        // Venter før vi tillater transaksjonen som holder på låsen å avslutte.
        TimeUnit.MILLISECONDS.sleep(FORSINKELSE).let {
            completer.complete(Any())
        }

        val completed = countDownLatch.await(1L, TimeUnit.SECONDS)
        completed `should be` true

        Duration.between(forsteTimestamp, andreTimestamp)
            .toMillis() `should be less than` FORSINKELSE
    }

    private fun doInTransaction(function: () -> Unit) {
        transactionTemplate.execute {
            function()
        }
    }
}
