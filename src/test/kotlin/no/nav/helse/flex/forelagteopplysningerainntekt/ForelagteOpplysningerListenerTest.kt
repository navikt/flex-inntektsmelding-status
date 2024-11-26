package no.nav.helse.flex.forelagteopplysningerainntekt

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.helse.flex.config.unleash.UnleashToggles
import org.amshove.kluent.`should be false`
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment

class ForelagteOpplysningerListenerTest {
    @Test
    fun `burde returnere false når unleash er togglet av`() {
        val unleashToggles =
            mock<UnleashToggles> {
                on { forelagteOpplysninger() } doReturn false
            }
        val listener =
            ForelagteOpplysningerListener(
                forelagteOpplysningerRepository = mock<ForelagteOpplysningerRepository>(),
                unleashToggles = unleashToggles,
            )

        listener.listen(cr = mock<ConsumerRecord<String, String>>(), acknowledgment = mock<Acknowledgment>())
            .`should be false`()
    }
}
