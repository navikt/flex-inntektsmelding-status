package no.nav.helse.flex.brukervarsel

import no.nav.helse.flex.kafka.MINSIDE_BRUKERVARSEL
import no.nav.helse.flex.logger
import no.nav.helse.flex.varseltekst.*
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
) {
    val log = logger()

    fun beskjedManglerInntektsmelding(
        fnr: String,
        bestillingId: String,
        orgNavn: String,
        fom: LocalDate,
        synligFremTil: Instant,
        forsinketSaksbehandling: Boolean,
        brukEksternVarsling: Boolean,
    ) {
        val opprettVarsel =
            VarselActionBuilder.opprett {
                type = Varseltype.Beskjed
                varselId = bestillingId
                sensitivitet = Sensitivitet.High
                ident = fnr
                tekst =
                    Tekst(
                        spraakkode = "nb",
                        tekst =
                            if (forsinketSaksbehandling) {
                                skapVenterPåInntektsmelding28Tekst(orgNavn)
                            } else {
                                skapVenterPåInntektsmelding15Tekst(fom, orgNavn)
                            },
                        default = true,
                    )
                aktivFremTil = synligFremTil.atZone(UTC)
                link = inntektsmeldingManglerUrl
                eksternVarsling =
                    if (brukEksternVarsling) {
                        EksternVarslingBestilling()
                    } else {
                        null
                    }
            }

        kafkaProducer.send(ProducerRecord(MINSIDE_BRUKERVARSEL, bestillingId, opprettVarsel)).get()
        log.info("Bestilte beskjed for manglende inntektsmelding $bestillingId")
    }

    fun beskjedForsinketSaksbehandling(
        fnr: String,
        bestillingId: String,
        synligFremTil: Instant,
        revarsel: Boolean,
    ) {
        val opprettVarsel =
            VarselActionBuilder.opprett {
                type = Varseltype.Beskjed
                varselId = bestillingId
                sensitivitet = Sensitivitet.High
                ident = fnr
                tekst =
                    Tekst(
                        spraakkode = "nb",
                        tekst =
                            if (revarsel) {
                                skapRevarselForsinketSaksbehandlingTekst()
                            } else {
                                skapForsinketSaksbehandling28Tekst()
                            },
                        default = true,
                    )
                aktivFremTil = synligFremTil.atZone(UTC)
                link = SAKSBEHANDLINGSTID_URL
                eksternVarsling = EksternVarslingBestilling()
            }

        kafkaProducer.send(ProducerRecord(MINSIDE_BRUKERVARSEL, bestillingId, opprettVarsel)).get()
        log.info("Bestilte beskjed for forsinket saksbehandling $bestillingId")
    }

    fun sendDonemelding(
        fnr: String,
        bestillingId: String,
    ) {
        val inaktiverVarsel =
            VarselActionBuilder.inaktiver {
                varselId = bestillingId
            }

        kafkaProducer.send(ProducerRecord(MINSIDE_BRUKERVARSEL, bestillingId, inaktiverVarsel)).get()
    }
}
