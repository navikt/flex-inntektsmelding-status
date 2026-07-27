package no.nav.helse.flex.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

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
            override fun shutdown() {
                threadPoolExecutor.queue.clear()
                super.shutdown()
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
