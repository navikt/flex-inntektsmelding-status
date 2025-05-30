package no.nav.helse.flex.forelagteopplysningerainntekt

import org.postgresql.util.PGobject
import java.time.Instant

fun lagTestForelagteOpplysninger(
    id: String = "test-id",
    statusEndret: Instant = Instant.parse("2024-01-01T00:00:00.00Z"),
    status: ForelagtStatus = ForelagtStatus.NY,
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
        statusEndret = statusEndret,
        status = status,
        opprinneligOpprettet = opprinneligOpprettet,
    )
