package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.sykepengesoknad.kafka.*
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeFalse
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
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
        val meldingFraDb: ForelagteOpplysningerMelding = objectMapper.readValue(record.forelagteOpplysningerMelding.value!!)

        meldingFraDb `should be equal to` forelagteOpplysningerMelding
    }
}
