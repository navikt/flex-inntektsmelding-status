package no.nav.helse.flex.forelagteopplysningerainntekt

import ForelagteOpplysningerMelding
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("forelagte_opplysninger_ainntekt")
data class ForelagteOpplysningerDbRecord(
    @Id
    val id: String? = null,
    // TODO: Kan fnr finnes her?
    val fnr: String? = null,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val forelagteOpplysningerMelding: PGobject,
    val opprettet: Instant,
    val forelagt: Instant?,
) {
    companion object {
        fun parseConsumerRecord(consumerRecord: ConsumerRecord<String, String>): ForelagteOpplysningerDbRecord {
            val forelagteOpplysningerMelding: ForelagteOpplysningerMelding = objectMapper.readValue(consumerRecord.value())
            return ForelagteOpplysningerDbRecord(
                vedtaksperiodeId = forelagteOpplysningerMelding.vedtaksperiodeId,
                behandlingId = forelagteOpplysningerMelding.behandlingId,
                forelagteOpplysningerMelding =
                PGobject().also {
                    it.type = "json"
                    it.value = consumerRecord.value()
                },
                opprettet = Instant.now(),
                forelagt = null,
            )
        }
    }
}
