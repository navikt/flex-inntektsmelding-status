package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.helse.flex.config.unleash.UnleashToggles
import org.junit.jupiter.api.Test

class ForelagteOpplysningerListenerTest {
    @Test
    fun `burde returnere når unleash er togglet av`(){
        val unleasToggles = mock<UnleashToggles> {
            on { forelagteOpplysninger() } doReturn false
        }
        ForelagteOpplysningerListener(mock<ForelagteOpplysningerRepository>(), unleasToggles)
    }
}
