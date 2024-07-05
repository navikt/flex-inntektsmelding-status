package no.nav.helse.flex

import no.nav.helse.flex.varselutsending.CronJobStatus.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ThrottleVarsleTest : FellesTestOppsett() {
    @Test
    @Order(1)
    fun `Vi sender inn 6 søknader som venter på arbeidsgiver`() {
        (0 until 6).forEach { index -> sendSoknaderSomVenterPaArbeidsgiver(index) }

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(6)
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 4
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 6
        cronjobResultat[THROTTLET_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL] shouldBeEqualTo 2
        varslingConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
    }
}
