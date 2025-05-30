package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagtStatus
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component

@Component
class TotaltAntallForelagteOpplysningerSjekk(
    private val maxAntallForelagteOpplysninger: Int = 500,
) {
    private val log = logger()

    fun sjekk(forelagteOpplysninger: List<ForelagteOpplysningerDbRecord>) {
        val forelagteOpplysningerSkalForelegges = forelagteOpplysninger.filter { it.status == ForelagtStatus.SKAL_FORELEGGES }
        if (forelagteOpplysningerSkalForelegges.size > maxAntallForelagteOpplysninger) {
            val message =
                "Sjekk feilet: For mange forelagte opplysninger skal sendes ut på en gang. " +
                    "Antall: ${forelagteOpplysningerSkalForelegges.size}"
            log.error(message)
            throw RuntimeException(message)
        } else {
            log.info("Antall forelagte opplysninger som sendes ut på en gang: ${forelagteOpplysningerSkalForelegges.size}")
        }
    }
}
