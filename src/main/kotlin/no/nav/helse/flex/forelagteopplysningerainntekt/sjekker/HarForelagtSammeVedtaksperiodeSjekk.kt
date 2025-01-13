package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

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
            .filter { it.forelagt != null }
            .any { it.vedtaksperiodeId == vedtaksperiodeId && it.id != forelagteOpplysningerId }
    }
}
