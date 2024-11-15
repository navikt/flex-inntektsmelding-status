package no.nav.helse.flex.forelagteopplysningerainntekt

import ForelagteOpplysningerMelding
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeFalse
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.*
import java.util.concurrent.TimeUnit

class ForelagteOpplysningerTest : FellesTestOppsett() {
    @Test
    fun `Tar imot og lagrer forelagte inntektsopplysninger fra ainntekt`() {
        val forelagteOpplysningerMelding =
            ForelagteOpplysningerMelding(
                vedtaksperiodeId = UUID.randomUUID().toString(),
                behandlingId = UUID.randomUUID().toString(),
                tidsstempel = LocalDateTime.now(),
                omregnetÅrsinntekt = 500000.0,
                skatteinntekter =
                listOf(
                    ForelagteOpplysningerMelding.Skatteinntekt(
                        måned = YearMonth.of(2024, 1),
                        beløp = 42000.0,
                    ),
                    ForelagteOpplysningerMelding.Skatteinntekt(
                        måned = YearMonth.of(2024, 2),
                        beløp = 43000.0,
                    ),
                ),
            )

        forelagteOpplysningerRepository.existsByVedtaksperiodeIdAndBehandlingId(
            vedtaksperiodeId = forelagteOpplysningerMelding.vedtaksperiodeId,
            behandlingId = forelagteOpplysningerMelding.behandlingId,
        ).shouldBeFalse()

        kafkaProducer.send(
            ProducerRecord(
                FORELAGTE_OPPLYSNINGER_TOPIC,
                null,
                forelagteOpplysningerMelding.vedtaksperiodeId,
                forelagteOpplysningerMelding.serialisertTilString(),
            ),
        ).get()

        await().atMost(10, TimeUnit.SECONDS).until {
            forelagteOpplysningerRepository.existsByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = forelagteOpplysningerMelding.vedtaksperiodeId,
                behandlingId = forelagteOpplysningerMelding.behandlingId,
            )
        }

        val record =
            forelagteOpplysningerRepository.findAll().toList().first {
                it.vedtaksperiodeId == forelagteOpplysningerMelding.vedtaksperiodeId
            }

        record.behandlingId `should be equal to` forelagteOpplysningerMelding.behandlingId
        val meldingFraDb: ForelagteOpplysningerMelding =
            objectMapper.readValue(record.forelagteOpplysningerMelding.value!!)

        meldingFraDb `should be equal to` forelagteOpplysningerMelding
    }

    @Test
    fun `Henter og sender ut brukernotifikasjon om forelagte inntektsopplysninger fra ainntekt`() {
        val forelagteOpplysningerMelding =
            ForelagteOpplysningerDbRecord(
                vedtaksperiodeId = "hent-test-opplysning",
                behandlingId = "hent-test-opplysning",
                forelagteOpplysningerMelding = PGobject().apply { type = "json"; value = "{}" },
                opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
                forelagt = null,
            )

        forelagteOpplysningerRepository.save(forelagteOpplysningerMelding)

        sendForelagteOpplysningerCronjob.runMedParameter(Instant.parse("2024-11-15T12:00:00.00Z"))

        meldingKafkaConsumer.ventPåRecords(antall = 1, Duration.ofSeconds(9)).first().let {
            it `should not be` null
            val kafkamelding: MeldingKafkaDto = objectMapper.readValue(it.value())
            kafkamelding.opprettMelding?.metadata?.get("vedtaksperiodeId") `should be equal to` "hent-test-opplysning"
        }
    }
}
