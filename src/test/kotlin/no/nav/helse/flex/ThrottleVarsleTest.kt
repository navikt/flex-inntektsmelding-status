package no.nav.helse.flex

import no.nav.helse.flex.varselutsending.CronJobStatus.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
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
        cronjobResultat[FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN] shouldBeEqualTo 6
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 6
        cronjobResultat[THROTTLET_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL] shouldBeEqualTo 2
        varslingConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
    }

    @Test
    @Order(3)
    fun `Vi sender ut de to siste inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 2
        cronjobResultat[FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN] shouldBeEqualTo 2
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 2
        cronjobResultat[THROTTLET_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL].shouldBeNull()
        varslingConsumer.ventPåRecords(2, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(2, duration = Duration.ofMinutes(1))
    }

    @Test
    @Order(4)
    fun `Vi sender ut de andre inntektsmelding varsel etter 29 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(29))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING].shouldBeNull()
        cronjobResultat[FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[THROTTLET_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL].shouldBeNull()
        cronjobResultat[SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 4
        cronjobResultat[ANDRE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN] shouldBeEqualTo 6
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 6
        cronjobResultat[THROTTLET_ANDRE_MANGLER_INNTEKTSMELDING_VARSEL] shouldBeEqualTo 2
        // Donner
        varslingConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        // Nye varsler
        varslingConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
    }

    @Test
    @Order(5)
    fun `Vi sender ut de to siste andre inntektsmelding varsel etter 29 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(29))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING].shouldBeNull()
        cronjobResultat[FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[THROTTLET_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL].shouldBeNull()
        cronjobResultat[SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 2
        cronjobResultat[ANDRE_MANGLER_INNTEKTSMELDING_VARSEL_DRY_RUN] shouldBeEqualTo 2
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 2
        cronjobResultat[THROTTLET_ANDRE_MANGLER_INNTEKTSMELDING_VARSEL].shouldBeNull()
        // Donner
        varslingConsumer.ventPåRecords(2, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(2, duration = Duration.ofMinutes(1))
        // Nye varsler
        varslingConsumer.ventPåRecords(2, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(2, duration = Duration.ofMinutes(1))
    }
}
