package no.nav.helse.flex.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

@Configuration
@EnableAsync
class SchedulerConfig {
    @Bean
    @Primary
    fun taskScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("flex-inntektsmelding-status-scheduled-task-")
            initialize()
        }

    @Bean
    fun varselutsendingTaskExecutor(): ThreadPoolTaskExecutor =
        object : ThreadPoolTaskExecutor() {
            private val nedstenging = AtomicBoolean(false)

            override fun shutdown() {
                nedstenging.set(true)
                threadPoolExecutor.queue.clear()
                super.shutdown()
            }

            override fun <T> submitCompletable(task: java.util.concurrent.Callable<T>): CompletableFuture<T> {
                if (nedstenging.get()) return CompletableFuture.failedFuture(IllegalStateException("Executor stengt, task avvist"))
                return super.submitCompletable(task)
            }
        }.apply {
            corePoolSize = 5
            maxPoolSize = 5
            setThreadNamePrefix("varselutsending-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(20)
            initialize()
        }
}
