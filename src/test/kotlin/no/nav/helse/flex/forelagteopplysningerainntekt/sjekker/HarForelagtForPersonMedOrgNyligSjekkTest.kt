package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Instant
import java.util.*

class HarForelagtForPersonMedOrgNyligSjekkTest {
    @Test
    fun `burde ikke være gjyldig dersom nylig forelagt`() {
        val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson =
            mock {
                on { hentAlleForelagteOpplysningerFor("_", "_") } doReturn
                    listOf(
                        lagTestForelagteOpplysninger(forelagt = Instant.parse("2024-01-01T00:00:00.00Z")),
                    )
            }
        val harForelagtForPersonMedOrgNyligSjekk = HarForelagtForPersonMedOrgNyligSjekk(hentAlleForelagteOpplysningerForPerson)

        harForelagtForPersonMedOrgNyligSjekk.sjekk(
            "_", "_", now = Instant.parse("2024-01-28T00:00:00.00Z"),
        ) `should be` false
    }

    @Test
    fun `burde være gjyldig dersom forelagt for lenge nok siden`() {
        val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson =
            mock {
                on { hentAlleForelagteOpplysningerFor("_", "_") } doReturn
                    listOf(
                        lagTestForelagteOpplysninger(forelagt = Instant.parse("2024-01-01T00:00:00.00Z")),
                    )
            }
        val harForelagtForPersonMedOrgNyligSjekk = HarForelagtForPersonMedOrgNyligSjekk(hentAlleForelagteOpplysningerForPerson)

        harForelagtForPersonMedOrgNyligSjekk.sjekk(
            "_", "_", now = Instant.parse("2024-01-29T00:00:00.00Z"),
        ) `should be` true
    }
}

private fun lagTestForelagteOpplysninger(
    id: String = UUID.randomUUID().toString(),
    forelagt: Instant? = null,
): ForelagteOpplysningerDbRecord {
    return ForelagteOpplysningerDbRecord(
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
        opprinneligOpprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
    )
}
