package no.nav.helse.flex.util

import java.util.concurrent.CompletableFuture

fun <T> ventPaAlle(futures: List<CompletableFuture<T>>): List<T> {
    CompletableFuture.allOf(*futures.toTypedArray()).join()
    return futures.map { it.get() }
}
