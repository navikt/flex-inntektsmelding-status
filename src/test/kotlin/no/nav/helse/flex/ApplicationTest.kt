package no.nav.helse.flex

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@DirtiesContext
class ApplicationTest : FellesTestOppsett() {

    @Test
    fun contextLoads() {
    }
}
