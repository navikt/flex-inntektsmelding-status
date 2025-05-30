package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagtStatus
import org.springframework.stereotype.Component

@Component
class HarForelagtSammeVedtaksperiodeSjekk(
    private val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson,
) {
    fun sjekk(
        fnr: String,
        vedtaksperiodeId: String,
        forelagteOpplysningerId: String,
    ): Boolean {
        val alleForelagte =
            hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(fnr = fnr)
        return alleForelagte
            .filter { it.status == ForelagtStatus.SENDT }
            .any { it.vedtaksperiodeId == vedtaksperiodeId && it.id != forelagteOpplysningerId }
    }
}
