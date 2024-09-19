package no.nav.helse.flex

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import javax.management.ObjectName

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

        val javaOptions = System.getenv("JDK_JAVA_OPTIONS")
        val availableProcessors = Runtime.getRuntime().availableProcessors()

        val flags =
            ManagementFactory.getPlatformMBeanServer().invoke(
                ObjectName.getInstance("com.sun.management:type=DiagnosticCommand"),
                "vmFlags",
                arrayOf(null),
                arrayOf("[Ljava.lang.String;"),
            ) as String

        val gcFlags =
            flags.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().find { it.contains("GC") }

        val gcPairs =
            ManagementFactory.getGarbageCollectorMXBeans()
                .map { b -> Pair(b.name, b.memoryPoolNames.contentToString()) }

        logger().info("EnvironmentDebug: JDK_JAVA_OPTIONS: $javaOptions")
        logger().info("EnvironmentDebug: availableProcessors: $availableProcessors")
        logger().info("EnvironmentDebug: GC: $gcFlags")
        logger().info("EnvironmentDebug: GC beans: $gcPairs")
    }
}
