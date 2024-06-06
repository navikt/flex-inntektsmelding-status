package no.nav.helse.flex.inntektsmelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
class LagreInntektsmeldingerFraKafka(
    val inntektsmeldingRepository: InntektsmeldingRepository,
) {
    val log = logger()

    fun oppdater(value: String) {
        val inntektsmelding: Inntektsmelding = objectMapper.readValue(value)
        if (inntektsmeldingRepository.existsByInntektsmeldingId(inntektsmelding.inntektsmeldingId)) {
            log.info("Inntektsmelding med id ${inntektsmelding.inntektsmeldingId} finnes allerede i databasen")
            return
        }

        val beregnetInntektPerMnd = inntektsmelding.beregnetInntekt?.toInt() ?: 0
        val refusjonPerMnd = inntektsmelding.refusjon.beloepPrMnd?.toInt() ?: 0
        val fullRefusjon = beregnetInntektPerMnd == refusjonPerMnd

        inntektsmeldingRepository.save(
            InntektsmeldingDbRecord(
                inntektsmeldingId = inntektsmelding.inntektsmeldingId,
                mottattDato = inntektsmelding.mottattDato.atZone(ZoneId.of("Europe/Oslo")).toInstant(),
                opprettet = Instant.now(),
                fnr = inntektsmelding.arbeidstakerFnr,
                arbeidsgivertype = inntektsmelding.arbeidsgivertype.toString(),
                fullRefusjon = fullRefusjon,
                virksomhetsnummer = inntektsmelding.virksomhetsnummer,
                foersteFravaersdag = inntektsmelding.foersteFravaersdag,
                vedtaksperiodeId = inntektsmelding.vedtaksperiodeId?.toString(),
            ),
        )
        log.info("Lagret inntektsmelding med id ${inntektsmelding.inntektsmeldingId} i databasen")
    }
}
