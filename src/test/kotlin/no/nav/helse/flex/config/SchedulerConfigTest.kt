package no.nav.helse.flex.config

import no.nav.helse.flex.testconfig.ScheduledTasks
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.`should be true`
import org.awaitility.Awaitility.await
import org.awaitility.Durations
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

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
}
