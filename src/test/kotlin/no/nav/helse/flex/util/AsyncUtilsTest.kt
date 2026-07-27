package no.nav.helse.flex.util

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AsyncUtilsTest {
    private val executor = Executors.newFixedThreadPool(3)

    @AfterEach
    fun tearDown() {
        executor.shutdown()
    }

    @Test
    fun `ventPaAlle returnerer alle resultater i riktig rekkefølge`() {
        val futures =
            listOf(
                CompletableFuture.completedFuture("a"),
                CompletableFuture.completedFuture("b"),
                CompletableFuture.completedFuture("c"),
            )
        ventPaAlle(futures) shouldBeEqualTo listOf("a", "b", "c")
    }

    @Test
    fun `ventPaAlle venter på alle futures selv om én feiler - langsom jobb fullfører`() {
        val langJobbFerdig = AtomicBoolean(false)

        val feiler =
            CompletableFuture<String>().also {
                it.completeExceptionally(RuntimeException("noe gikk galt"))
            }
        val langsom =
            CompletableFuture.supplyAsync({
                Thread.sleep(300)
                langJobbFerdig.set(true)
                "langsom ok"
            }, executor)

        val start = System.currentTimeMillis()
        assertThrows<CompletionException> {
            ventPaAlle(listOf(feiler, langsom))
        }
        val elapsed = System.currentTimeMillis() - start

        assert(elapsed >= 280) { "ventPaAlle returnerte etter ${elapsed}ms – ventet ikke på langsom jobb" }
        langJobbFerdig.get() shouldBeEqualTo true
    }

    @Test
    fun `ventPaAlle kaster exception med original årsak`() {
        val feiler =
            CompletableFuture<String>().also {
                it.completeExceptionally(RuntimeException("noe gikk galt"))
            }

        val exception =
            assertThrows<CompletionException> {
                ventPaAlle(listOf(CompletableFuture.completedFuture("ok"), feiler))
            }

        exception.cause!!.message shouldBeEqualTo "noe gikk galt"
    }
}
