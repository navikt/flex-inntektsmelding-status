package no.nav.helse.flex.testconfig

import org.springframework.boot.test.context.TestComponent
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@EnableScheduling
@TestComponent
class ScheduledTasks {
    val oppgaveUtfort = AtomicBoolean(false)
    val enkelOppgaveTid = AtomicInteger(0)

    private var antallOppgaverSomSkalKjores: Int = 0
    private var oppgaveFullfortMarkorer: MutableList<AtomicBoolean>? = null
    private lateinit var fullforingKlokkelas: CountDownLatch

    @Scheduled(fixedRate = 100)
    fun enkelOppgave() {
        val startTid = System.currentTimeMillis()
        Thread.sleep(30)
        oppgaveUtfort.set(true)
        enkelOppgaveTid.set((System.currentTimeMillis() - startTid).toInt())
    }

    fun forberedKonkurrerendeOppgaver(antall: Int) {
        antallOppgaverSomSkalKjores = antall
        oppgaveFullfortMarkorer = MutableList(antall) { AtomicBoolean(false) }
        fullforingKlokkelas = CountDownLatch(antall)
    }

    @Scheduled(fixedRate = 30)
    fun konkurrerendeOppgave() {
        val markorer = oppgaveFullfortMarkorer ?: return
        if (antallOppgaverSomSkalKjores == 0) return

        val forsteUfullforteOppgaveIndeks = markorer.indexOfFirst { !it.get() }
        if (forsteUfullforteOppgaveIndeks == -1) return

        markorer[forsteUfullforteOppgaveIndeks].set(true)
        fullforingKlokkelas.countDown()
    }

    fun ventPaAlleKonkurrerendeOppgaver(timeoutMillis: Long) {
        val markorer =
            oppgaveFullfortMarkorer
                ?: throw IllegalStateException("Konkurrerende oppgaver er ikke forberedt.")

        val fullfortInnenTid = fullforingKlokkelas.await(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!fullfortInnenTid) {
            throw TimeoutException("Ikke alle konkurrerende oppgaver ble fullfort innen tidsfristen.")
        }
    }

    fun alleKonkurrerendeOppgaverUtfort(): Boolean = oppgaveFullfortMarkorer?.all { it.get() } ?: false
}
