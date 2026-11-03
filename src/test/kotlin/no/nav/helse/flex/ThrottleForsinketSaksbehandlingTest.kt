package no.nav.helse.flex

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ThrottleForsinketSaksbehandlingTest : FellesTestOppsett() {
    @Test
    @Order(1)
    fun `Vi sender inn 6 søknader som venter på saksbehandler`() {
        (0 until 6).forEach { index -> sendSoknaderSomVenterPaSaksbehandler(index) }

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(6)
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler  varsel etter 30 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(30))
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBe 4
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBe 6
        cronjobResultat[THROTTLET_FØRSTE_FORSINKER_SAKSBEHANDLING_VARSEL] shouldBe 2
        varslingConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
    }
}
