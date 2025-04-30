package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Instant
import java.util.UUID

class ForsinkelseFraOpprinnelseTilVarselSjekkTest {
    @Test
    fun `burde ikke være gyldig dersom det er lang forsinkelse`() {
        val forsinkelseFraOpprinnelseTilVarselSjekk = ForsinkelseFraOpprinnelseTilVarselSjekk()
        val forelagteOpplysninger =
            lagTestForelagteOpplysninger(
                opprinneligOpprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            )
        val langForsinkelseSjekk =
            forsinkelseFraOpprinnelseTilVarselSjekk.sjekk(
                forelagteOpplysninger,
                Instant.parse("2024-01-07T00:00:00.00Z"),
            )
        langForsinkelseSjekk.`should be false`()
    }

    @Test
    fun `burde være gyldig dersom det er kort forsinkelse`() {
        val forsinkelseFraOpprinnelseTilVarselSjekk = ForsinkelseFraOpprinnelseTilVarselSjekk()
        val forelagteOpplysninger =
            lagTestForelagteOpplysninger(
                opprinneligOpprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            )
        val langForsinkelseSjekk =
            forsinkelseFraOpprinnelseTilVarselSjekk.sjekk(
                forelagteOpplysninger,
                Instant.parse("2024-01-06T00:00:00.00Z"),
            )
        langForsinkelseSjekk.`should be true`()
    }
}

private fun lagTestForelagteOpplysninger(
    id: String = UUID.randomUUID().toString(),
    forelagt: Instant? = null,
    opprinneligOpprettet: Instant = Instant.parse("2024-01-01T00:00:00.00Z"),
): ForelagteOpplysningerDbRecord =
    ForelagteOpplysningerDbRecord(
        id = id,
        vedtaksperiodeId = "_",
        behandlingId = "_",
        forelagteOpplysningerMelding =
            PGobject().apply {
                type = "json"
                value = "{}"
            },
        opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
        forelagt = forelagt,
        opprinneligOpprettet = opprinneligOpprettet,
    )
