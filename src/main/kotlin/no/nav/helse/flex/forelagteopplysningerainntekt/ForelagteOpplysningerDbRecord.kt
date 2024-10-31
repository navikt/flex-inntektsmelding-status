package no.nav.helse.flex.forelagteopplysningerainntekt

import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant



@Table("FORELAGTE_OPPLYSNINGER_AINNTEKT")
data class ForelagteOpplysningerDbRecord(
    @Id
    val id: String? = null,
    val fnr: String? = null,
    val melding: String,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val forelagteOpplysningerMelding: PGobject,
    val opprettet: Instant,
    val forelagt: Instant?,
)
