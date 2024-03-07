package no.nav.helse.flex.brukernotifikasjon

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.kafka.MINSIDE_BRUKERVARSEL
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.norskDateFormat
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC

@Component
class Brukernotifikasjon(
    private val kafkaProducer: KafkaProducer<String, String>,
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

        val opprettVarsel =
            VarselActionBuilder.opprett {
                type = Varseltype.Beskjed
                varselId = bestillingId
                sensitivitet = Sensitivitet.High
                ident = fnr
                tekst =
                    Tekst(
                        spraakkode = "nb",
                        tekst = "Saksbehandlingen er forsinket fordi vi mangler inntektsmeldingen fra $orgNavn for sykefraværet som startet ${
                            fom.format(
                                norskDateFormat,
                            )
                        }.",
                        default = true,
                    )
                aktivFremTil = synligFremTil.atZone(UTC)
                link = inntektsmeldingManglerUrl
                eksternVarsling = EksternVarslingBestilling()
            }

        kafkaProducer.send(ProducerRecord(MINSIDE_BRUKERVARSEL, bestillingId, opprettVarsel)).get()
        log.info("Bestilte beskjed for manglende inntektsmelding $eksternId")
    }

    fun sendDonemelding(
        fnr: String,
        eksternId: String,
        bestillingId: String,
    ) {
        registry.counter("brukernotifikasjon_done_sendt").increment()

        val inaktiverVarsel =
            VarselActionBuilder.inaktiver {
                varselId = bestillingId
            }

        kafkaProducer.send(ProducerRecord(MINSIDE_BRUKERVARSEL, bestillingId, inaktiverVarsel)).get()
    }
}
