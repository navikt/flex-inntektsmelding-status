package no.nav.helse.flex.brukernotifikasjon

import io.micrometer.core.instrument.MeterRegistry
import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.helse.flex.kafka.BRUKERNOTIFIKASJON_BESKJED_TOPIC
import no.nav.helse.flex.kafka.BRUKERNOTIFIKASJON_DONE_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.norskDateFormat
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

@Component
class Brukernotifikasjon(
    private val beskjedKafkaProducer: Producer<NokkelInput, BeskjedInput>,
    private val doneKafkaProducer: Producer<NokkelInput, DoneInput>,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
    private val registry: MeterRegistry,
) {
    val log = logger()

    fun beskjedManglerInntektsmelding(
        fnr: String,
        eksternId: String,
        bestillingId: String,
        orgNavn: String,
        fom: LocalDate,
        synligFremTil: Instant,
    ) {
        registry.counter("brukernotifikasjon_mangler_inntektsmelding_beskjed_sendt").increment()

        beskjedKafkaProducer.send(
            ProducerRecord(
                BRUKERNOTIFIKASJON_BESKJED_TOPIC,
                NokkelInputBuilder()
                    .withAppnavn("flex-inntektsmelding-status")
                    .withNamespace("flex")
                    .withFodselsnummer(fnr)
                    .withEventId(bestillingId)
                    .withGrupperingsId(bestillingId)
                    .build(),
                BeskjedInputBuilder()
                    .withTidspunkt(LocalDateTime.now())
                    .withTekst(
                        "Vi mangler inntektsmeldingen fra $orgNavn for sykefraværet som startet ${
                            fom.format(
                                norskDateFormat,
                            )
                        }.",
                    )
                    .withLink(URI(inntektsmeldingManglerUrl).toURL())
                    .withSikkerhetsnivaa(4)
                    .withSynligFremTil(synligFremTil.atOffset(UTC).toLocalDateTime())
                    .withEksternVarsling(false)
                    .build(),
            ),
        ).get()

        log.info("Bestilte beskjed for manglende inntektsmelding $eksternId")
    }

    fun sendDonemelding(
        fnr: String,
        eksternId: String,
        bestillingId: String,
    ) {
        registry.counter("brukernotifikasjon_done_sendt").increment()

        doneKafkaProducer.send(
            ProducerRecord(
                BRUKERNOTIFIKASJON_DONE_TOPIC,
                NokkelInputBuilder()
                    .withAppnavn("flex-inntektsmelding-status")
                    .withNamespace("flex")
                    .withFodselsnummer(fnr)
                    .withEventId(bestillingId)
                    .withGrupperingsId(bestillingId)
                    .build(),
                DoneInputBuilder()
                    .withTidspunkt(LocalDateTime.now())
                    .build(),
            ),
        ).get()
    }
}
