package no.nav.helse.flex.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.helse.flex.kafka.brukernotifikasjonBeskjedTopic
import no.nav.helse.flex.kafka.brukernotifikasjonDoneTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.norskDateFormat
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

@Component
class Brukernotifikasjon(
    private val beskjedKafkaProducer: Producer<NokkelInput, BeskjedInput>,
    private val doneKafkaProducer: Producer<NokkelInput, DoneInput>,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
) {
    val log = logger()

    fun beskjedManglerInntektsmelding(
        fnr: String,
        eksternId: String,
        orgNavn: String,
        fom: LocalDate,
        synligFremTil: Instant,
    ) {
        beskjedKafkaProducer.send(
            ProducerRecord(
                brukernotifikasjonBeskjedTopic,
                NokkelInputBuilder()
                    .withAppnavn("flex-inntektsmelding-status")
                    .withNamespace("flex")
                    .withFodselsnummer(fnr)
                    .withEventId(eksternId)
                    .withGrupperingsId(eksternId)
                    .build(),
                BeskjedInputBuilder()
                    .withTidspunkt(LocalDateTime.now())
                    .withTekst(
                        "Vi mangler inntektsmeldingen fra $orgNavn for sykefrav??r f.o.m. ${
                        fom.format(
                            norskDateFormat
                        )
                        }."
                    )
                    .withLink(URL(inntektsmeldingManglerUrl))
                    .withSikkerhetsnivaa(4)
                    .withSynligFremTil(synligFremTil.atOffset(UTC).toLocalDateTime())
                    .withEksternVarsling(false)
                    .build()
            )
        ).get()

        log.info("Bestilte beskjed for manglende inntektsmelding $eksternId")
    }

    fun sendDonemelding(
        fnr: String,
        eksternId: String,
    ) {
        doneKafkaProducer.send(
            ProducerRecord(
                brukernotifikasjonDoneTopic,
                NokkelInputBuilder()
                    .withAppnavn("flex-inntektsmelding-status")
                    .withNamespace("flex")
                    .withFodselsnummer(fnr)
                    .withEventId(eksternId)
                    .withGrupperingsId(eksternId)
                    .build(),
                DoneInputBuilder()
                    .withTidspunkt(LocalDateTime.now())
                    .build()
            )
        ).get()

        log.info("Inntektsmelding $eksternId har mottatt inntektsmelding, donet brukernotifikasjonen")
    }
}
