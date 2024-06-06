package no.nav.helse.flex.brukervarsel

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.kafka.MINSIDE_BRUKERVARSEL
import no.nav.helse.flex.logger
import no.nav.helse.flex.varseltekst.skapVenterPåInntektsmeldingTekst
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
class Brukervarsel(
    private val kafkaProducer: KafkaProducer<String, String>,
    @Value("\${INNTEKTSMELDING_MANGLER_URL}") private val inntektsmeldingManglerUrl: String,
    private val registry: MeterRegistry,
) {
    val log = logger()

    fun beskjedManglerInntektsmelding(
        fnr: String,
        bestillingId: String,
        orgNavn: String,
        fom: LocalDate,
        synligFremTil: Instant,
    ) {
        registry.counter("brukervarsel_mangler_inntektsmelding_beskjed_sendt").increment()

        val opprettVarsel =
            VarselActionBuilder.opprett {
                type = Varseltype.Beskjed
                varselId = bestillingId
                sensitivitet = Sensitivitet.High
                ident = fnr
                tekst =
                    Tekst(
                        spraakkode = "nb",
                        tekst = skapVenterPåInntektsmeldingTekst(fom, orgNavn),
                        default = true,
                    )
                aktivFremTil = synligFremTil.atZone(UTC)
                link = inntektsmeldingManglerUrl
                eksternVarsling = EksternVarslingBestilling()
            }

        kafkaProducer.send(ProducerRecord(MINSIDE_BRUKERVARSEL, bestillingId, opprettVarsel)).get()
        log.info("Bestilte beskjed for manglende inntektsmelding $bestillingId")
    }

    fun sendDonemelding(
        fnr: String,
        bestillingId: String,
    ) {
        registry.counter("brukervarsel_done_sendt").increment()

        val inaktiverVarsel =
            VarselActionBuilder.inaktiver {
                varselId = bestillingId
            }

        kafkaProducer.send(ProducerRecord(MINSIDE_BRUKERVARSEL, bestillingId, inaktiverVarsel)).get()
    }
}
