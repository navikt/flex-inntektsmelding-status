package no.nav.helse.flex.vedtak

import no.nav.helse.flex.inntektsmelding.*
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Component
import java.time.Instant

// wtf wtf wtf
@Component
class VedtakLagring(
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository,
) {
    val log = logger()

    fun handterVedtak(cr: ConsumerRecord<String, String>) {
        if (cr.erVedtakFattet()) {
            val vedtaket =
                try {
                    cr.value().tilVedtakFattetForEksternDto()
                } catch (e: Exception) {
                    throw RuntimeException("Kunne ikke deserialisere vedtak", e)
                }

            var dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(vedtaket.vedtaksperiodeId)?.id
            if (dbId == null) {
                throw Exception("Fant ikke inntektsmelding for vedtak, dette skal ikke skje")
            }

            inntektsmeldingStatusRepository.save(
            InntektsmeldingStatusDbRecord(
                inntektsmeldingId = dbId,
                opprettet = Instant.now(),
                status = StatusVerdi.VEDTAK_FATTET,
            ),
        )
        }
    }
}

private fun ConsumerRecord<String, String>.erVedtakFattet(): Boolean {
    return headers().any { header ->
        header.key() == "type" && String(header.value()) == "VedtakFattet"
    }
}
