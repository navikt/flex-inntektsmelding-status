package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.tilOsloLocalDateTime
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class ForsinkelseFraOpprinnelseTilVarselSjekk {
    private val log = logger()

    fun sjekk(
        forelagteOpplysninger: ForelagteOpplysningerDbRecord,
        now: Instant,
    ): Boolean {
        val dagerSidenMeldingFraSpleis =
            forelagteOpplysninger.opprinneligOpprettet.tilOsloLocalDateTime()
                .until(now.tilOsloLocalDateTime(), ChronoUnit.DAYS)
        when {
            dagerSidenMeldingFraSpleis >= 8 -> {
                log.error(
                    "Det er mer enn 8 dager siden ($dagerSidenMeldingFraSpleis) spleis sendte melding til vi varsler " +
                        "om forelagte opplysninger. Sjekk om systemet har vært nede over lengre tid. " +
                        "Forelagte opplysninger: ${forelagteOpplysninger.id}",
                )
                return false
            }

            dagerSidenMeldingFraSpleis >= 1 -> {
                log.warn(
                    "Det er mer enn 1 dag siden spleis sendte melding til vi varsler om forelagte opplysninger." +
                        "Sjekk om systemet har vært nede, eller om det er noe hikke. Forelagte opplysninger: ${forelagteOpplysninger.id}",
                )
            }
        }
        return true
    }
}
