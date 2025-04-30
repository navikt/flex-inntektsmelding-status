package no.nav.helse.flex.config.unleash

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.springframework.stereotype.Component

const val UNLEASH_CONTEXT_FORELAGTE_OPPLYSNINGER = "flex-inntektsmelding-status-forelagte-opplysninger"

@Component
class UnleashToggles(
    private val unleash: Unleash,
) {
    fun forelagteOpplysninger(): Boolean =
        unleash.isEnabled(
            UNLEASH_CONTEXT_FORELAGTE_OPPLYSNINGER,
            UnleashContext.builder().build(),
        )
}
