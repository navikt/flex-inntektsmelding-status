package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.kafka.*
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
import java.util.*

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
        cronjobResultat[SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 4
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 6
        cronjobResultat[UTELATTE_FNR_FORSINKET_SAKSBEHANDLING_THROTTLE] shouldBeEqualTo 2
        varslingConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
    }
}
