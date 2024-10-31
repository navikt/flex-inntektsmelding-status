package no.nav.helse.flex.forelagteopplysningerainntekt

import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("forelagte_opplysninger_ainntekt")
data class ForelagteOpplysningerDbRecord(
    @Id
    val id: String? = null,
    val fnr: String? = null,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val forelagteOpplysningerMelding: PGobject,
    val opprettet: Instant,
    val forelagt: Instant?,
)
