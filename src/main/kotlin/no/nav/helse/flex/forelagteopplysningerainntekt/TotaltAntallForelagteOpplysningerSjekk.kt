package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.logger
import org.springframework.stereotype.Component

@Component
class TotaltAntallForelagteOpplysningerSjekk(
    private val maxAntallForelagteOpplysninger: Int = 100,
) {
    private val log = logger()

    fun sjekk(forelagteOpplysninger: List<ForelagteOpplysningerDbRecord>) {
        val ikkeForelagteOpplysninger = forelagteOpplysninger.filter { it.forelagt == null }
        if (ikkeForelagteOpplysninger.size > maxAntallForelagteOpplysninger) {
            val message = "Sjekk feilet: For mange forelagte opplysninger skal sendes ut på en gang. Antall: ${ikkeForelagteOpplysninger.size}"
            log.error(message)
            throw RuntimeException(message)
        } else {
            log.info("Antall forelagte opplysninger som sendes ut på en gang: ${ikkeForelagteOpplysninger.size}")
        }
    }
}
