package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class HarForelagtForPersonMedOrgNyligSjekk(
    private val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson,
) {
    private val log = logger()

    fun sjekk(
        fnr: String,
        orgnummer: String,
        now: Instant,
    ): Boolean {
        val forrigeForeleggelse = finnForrigeForeleggelseFor(fnr, orgnummer)
        val sjekkForelagtEtter = now.minus(Duration.ofDays(2))
        if (forrigeForeleggelse != null &&
            forrigeForeleggelse.forelagt!!.isAfter(sjekkForelagtEtter)
        ) {
            val dagerSidenForeleggelse = ChronoUnit.DAYS.between(forrigeForeleggelse.forelagt, now)
            log.warn(
                "Person har blitt forelagt nylig, for $dagerSidenForeleggelse dager siden. " +
                    "Forrige forelagte opplysning id: ${forrigeForeleggelse.id}",
            )
            return false
        } else {
            return true
        }
    }

    private fun finnForrigeForeleggelseFor(
        fnr: String,
        orgnummer: String,
    ): ForelagteOpplysningerDbRecord? {
        val forelagteOpplysninger =
            hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(fnr = fnr, orgnr = orgnummer)
        val sisteForeleggelse =
            forelagteOpplysninger
                .filter { it.forelagt != null }
                .maxByOrNull { it.forelagt!! }
        return sisteForeleggelse
    }
}
