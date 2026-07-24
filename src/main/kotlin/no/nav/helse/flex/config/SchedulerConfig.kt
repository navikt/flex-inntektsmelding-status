package no.nav.helse.flex.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executors

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
    fun varselutsendingTaskExecutor(): ConcurrentTaskExecutor =
        ConcurrentTaskExecutor(
            Executors.newFixedThreadPool(5, Thread.ofPlatform().name("varselutsending-", 1).factory()),
        )
}
