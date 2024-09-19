package no.nav.helse.flex

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory

@SpringBootApplication
@EnableScheduling
@EnableJwtTokenValidation
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Component
class EnvironmentDebug {
    init {

        logger().info("EnvironmentDebug: JDK_JAVA_OPTIONS: {}", System.getenv("JDK_JAVA_OPTIONS"))

        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

        gcBeans.forEach({ gcBean: GarbageCollectorMXBean ->
            logger().info("EnvironmentDebug:: GarbageCollectorMXBean: {}", gcBean.name)
        })
    }
}
