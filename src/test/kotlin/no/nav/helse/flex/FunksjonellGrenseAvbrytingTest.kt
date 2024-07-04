package no.nav.helse.flex

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.*
import java.time.Instant
import java.time.OffsetDateTime

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FunksjonellGrenseAvbrytingTest : FellesTestOppsett() {
    @Test
    @Order(1)
    fun `Vi sender inn 8 søknader som venter på arbeidsgiver`() {
        (0 until 8).forEach { index -> sendSoknaderSomVenterPaArbeidsgiver(index) }

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(8)
    }

    @Test
    @Order(2)
    fun `Cronjobben aborter`() {
        val exception =
            assertThrows<RuntimeException> {
                varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(40))
            }
        exception.message shouldBeEqualTo "Funksjonell grense for antall varsler nådd, antall varsler: 8. Grensen er satt til 7"
    }
}
