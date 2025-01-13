package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.Instant
import java.util.*

class HarForelagtSammeVedtaksperiodeSjekkTest {
    @Test
    fun `Godtar at man har seg selv i listen over forelagte`() {
        val testForelagteOpplysninger =
            lagTestForelagteOpplysninger(forelagt = Instant.parse("2024-01-01T00:00:00.00Z"))
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
    fun `feiler om vi har samme vedtaksperiode på en annen forelate opplysninger`() {
        val testdata = lagTestForelagteOpplysninger(forelagt = Instant.parse("2024-01-01T00:00:00.00Z"))
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

private fun lagTestForelagteOpplysninger(
    id: String = UUID.randomUUID().toString(),
    forelagt: Instant? = null,
): ForelagteOpplysningerDbRecord {
    return ForelagteOpplysningerDbRecord(
        id = id,
        vedtaksperiodeId = "_",
        behandlingId = "_",
        forelagteOpplysningerMelding =
            PGobject().apply {
                type = "json"
                value = "{}"
            },
        opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
        forelagt = forelagt,
        opprinneligOpprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
    )
}
