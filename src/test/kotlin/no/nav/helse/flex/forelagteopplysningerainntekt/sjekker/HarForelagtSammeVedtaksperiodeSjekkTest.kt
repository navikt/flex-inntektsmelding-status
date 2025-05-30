package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagtStatus
import no.nav.helse.flex.forelagteopplysningerainntekt.lagTestForelagteOpplysninger
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import java.util.*

class HarForelagtSammeVedtaksperiodeSjekkTest {
    @Test
    fun `Godtar at man har seg selv i listen over forelagte`() {
        val testForelagteOpplysninger = lagTestForelagteOpplysninger()
        val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson =
            mock {
                on { hentAlleForelagteOpplysningerFor("_") } doReturn
                    listOf(
                        testForelagteOpplysninger,
                        testForelagteOpplysninger.copy(vedtaksperiodeId = "2"),
                    )
            }
        val harForelagtSammeVedtaksperiodeSjekk =
            HarForelagtSammeVedtaksperiodeSjekk(hentAlleForelagteOpplysningerForPerson)

        harForelagtSammeVedtaksperiodeSjekk.sjekk(
            "_",
            testForelagteOpplysninger.vedtaksperiodeId,
            testForelagteOpplysninger.id!!,
        ) `should be` false
    }

    @Test
    fun `feiler om vi har blitt bedt om flere forelegginger på samme vedtaksperiode med forskjellig behandlignsId`() {
        val testdata = lagTestForelagteOpplysninger(status = ForelagtStatus.SENDT)
        val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson =
            mock {
                on { hentAlleForelagteOpplysningerFor("_") } doReturn
                    listOf(
                        testdata,
                    )
            }
        val harForelagtSammeVedtaksperiodeSjekk =
            HarForelagtSammeVedtaksperiodeSjekk(hentAlleForelagteOpplysningerForPerson)

        harForelagtSammeVedtaksperiodeSjekk.sjekk(
            fnr = "_",
            vedtaksperiodeId = testdata.vedtaksperiodeId,
            forelagteOpplysningerId = UUID.randomUUID().toString(),
        ) `should be` true
    }
}
