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
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(classes = [SchedulerConfig::class, ScheduledTasks::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
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
    fun `umiddelbar oppgave pa planlegger utfores`() {
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
    fun `flere oppgaver kjorer samtidig pa samme planlegger`() {
        val antallOppgaver = 2
        val timeoutMillis = 800L

        planlagteOppgaver.forberedKonkurrerendeOppgaver(antallOppgaver)
        planlagteOppgaver.ventPaAlleKonkurrerendeOppgaver(timeoutMillis)
        planlagteOppgaver.alleKonkurrerendeOppgaverUtfort().`should be true`()
    }
}
