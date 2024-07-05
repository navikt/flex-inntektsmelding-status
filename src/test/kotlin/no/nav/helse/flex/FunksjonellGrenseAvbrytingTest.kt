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

        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
            .shouldHaveSize(8)
    }

    @Test
    @Order(2)
    fun `Cronjobben aborter`() {
        val exception =
            assertThrows<RuntimeException> {
                varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(40))
            }
        exception.message shouldBeEqualTo
            "Funksjonell grense for antall  SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15 varsler nådd, antall varsler: 8. Grensen er satt til 7"
    }

    @Test
    @Order(3)
    fun `Sletter fra database`() {
        slettFraDatabase()
    }

    @Test
    @Order(4)
    fun `Vi sender inn 8 søknader som venter på saksbehandler`() {
        (0 until 8).forEach { index -> sendSoknaderSomVenterPaSaksbehandler(index) }

        vedtaksperiodeBehandlingRepository.finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(Instant.now())
            .shouldHaveSize(8)
    }

    @Test
    @Order(5)
    fun `Cronjobben aborter igjen`() {
        val exception =
            assertThrows<RuntimeException> {
                varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(40))
            }
        exception.message shouldBeEqualTo
            "Funksjonell grense for antall  SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28 varsler nådd, antall varsler: 8. Grensen er satt til 7"
    }
}
