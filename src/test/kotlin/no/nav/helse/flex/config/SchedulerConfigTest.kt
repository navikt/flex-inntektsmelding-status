package no.nav.helse.flex.config

import no.nav.helse.flex.testconfig.ScheduledTasks
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.shouldBeEqualTo
import org.awaitility.Awaitility.await
import org.awaitility.Durations
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(classes = [SchedulerConfig::class, ScheduledTasks::class])
class SchedulerConfigTest {
    @Autowired
    private lateinit var oppgavePlanlegger: TaskScheduler

    @Autowired
    private lateinit var planlagteOppgaver: ScheduledTasks

    @Test
    fun `planlegger bean finnes og er ThreadPoolTaskScheduler`() {
        oppgavePlanlegger `should be instance of` ThreadPoolTaskScheduler::class
    }

    @Test
    fun `umiddelbar oppgave på planlegger utfores`() {
        val scheduler = oppgavePlanlegger as ThreadPoolTaskScheduler
        val oppgaveUtfort = AtomicBoolean(false)

        scheduler.schedule(
            { oppgaveUtfort.set(true) },
            Instant.now().plusMillis(100),
        )

        await()
            .atMost(Durations.TWO_HUNDRED_MILLISECONDS)
            .until { oppgaveUtfort.get() }

        oppgaveUtfort.get().`should be true`()
    }

    @Test
    fun `planlagt oppgave utfores`() {
        await()
            .atMost(Durations.TWO_HUNDRED_MILLISECONDS)
            .until { planlagteOppgaver.oppgaveUtfort.get() }

        planlagteOppgaver.oppgaveUtfort.get().`should be true`()
    }

    @Test
    fun `lang jobb blokkerer ikke kort jobb`() {
        val scheduler = oppgavePlanlegger as ThreadPoolTaskScheduler
        val kortJobbUtfort = AtomicBoolean(false)
        val langJobbStartet = AtomicBoolean(false)
        val slippLangJobbLos = CountDownLatch(1)

        try {
            // Simulerer VarselutsendingCronJob som holder en tråd opptatt lenge
            scheduler.schedule(
                {
                    langJobbStartet.set(true)
                    slippLangJobbLos.await()
                },
                Instant.now(),
            )

            await().atMost(Durations.ONE_SECOND).until { langJobbStartet.get() }

            // Simulerer SendForelagteOpplysningerCronjob som skal kjøre mens lang jobb pågår
            scheduler.schedule(
                { kortJobbUtfort.set(true) },
                Instant.now(),
            )

            // Kort jobb skal fullføres på den andre tråden mens lang jobb fortsatt kjører
            await().atMost(Durations.ONE_SECOND).until { kortJobbUtfort.get() }
            kortJobbUtfort.get().`should be true`()
        } finally {
            slippLangJobbLos.countDown()
        }
    }

    @Test
    fun `shutdown clearer køen - inflight fullfører, køede tasks kjøres ikke`() {
        val executor = SchedulerConfig().varselutsendingTaskExecutor()

        val slippLøs = CountDownLatch(1)
        val alleInflightStartet = CountDownLatch(5)
        val infligtFerdig = AtomicInteger(0)
        val queuedKjorte = AtomicBoolean(false)

        repeat(5) {
            executor.submit {
                alleInflightStartet.countDown()
                slippLøs.await(10, TimeUnit.SECONDS)
                infligtFerdig.incrementAndGet()
            }
        }
        alleInflightStartet.await()

        repeat(3) {
            executor.submit { queuedKjorte.set(true) }
        }

        val shutdownThread = Thread { executor.shutdown() }
        shutdownThread.start()

        await().atMost(Durations.ONE_SECOND).until {
            executor.threadPoolExecutor.queue.isEmpty()
        }

        slippLøs.countDown()
        shutdownThread.join(10_000)

        infligtFerdig.get() shouldBeEqualTo 5
        queuedKjorte.get() shouldBeEqualTo false
    }

    @Test
    fun `shutdown fra scheduler-tråd - inflight fullfører, køede droppes, nye avvises uten exception`() {
        val config = SchedulerConfig()
        val executor = config.varselutsendingTaskExecutor()
        val scheduler = config.taskScheduler() as ThreadPoolTaskScheduler

        val alleInflightStartet = CountDownLatch(5)
        val treTasksIKo = CountDownLatch(1)
        val slippLøs = CountDownLatch(1)
        val infligtFerdig = AtomicInteger(0)
        val queuedKjorte = AtomicBoolean(false)
        val postShutdownKjorte = AtomicBoolean(false)
        val postShutdownGikkBra = AtomicBoolean(false)
        val cronJobbFerdig = CountDownLatch(1)

        scheduler.schedule({
            try {
                repeat(5) {
                    executor.submitCompletable<Unit> {
                        alleInflightStartet.countDown()
                        slippLøs.await(10, TimeUnit.SECONDS)
                        infligtFerdig.incrementAndGet()
                    }
                }
                alleInflightStartet.await()

                repeat(3) { executor.submit { queuedKjorte.set(true) } }
                treTasksIKo.countDown()

                await().atMost(Durations.TWO_SECONDS).until { executor.threadPoolExecutor.isShutdown }

                try {
                    val future = executor.submitCompletable<Unit> { postShutdownKjorte.set(true) }
                    postShutdownGikkBra.set(future.isCompletedExceptionally)
                } catch (_: Exception) {
                }
            } finally {
                cronJobbFerdig.countDown()
            }
        }, Instant.now())

        alleInflightStartet.await()
        treTasksIKo.await()

        val shutdownThread = Thread { executor.shutdown() }
        shutdownThread.start()

        await().atMost(Durations.ONE_SECOND).until { executor.threadPoolExecutor.queue.isEmpty() }

        slippLøs.countDown()
        cronJobbFerdig.await(15, TimeUnit.SECONDS)
        shutdownThread.join(15_000)

        infligtFerdig.get() shouldBeEqualTo 5
        queuedKjorte.get() shouldBeEqualTo false
        postShutdownKjorte.get() shouldBeEqualTo false
        postShutdownGikkBra.get() shouldBeEqualTo true

        scheduler.destroy()
    }
}
