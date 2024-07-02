package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.Testdata
import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class FinnLikesteInntektsmeldingTest {
    val vedtaksperiodeBehandling =
        FullVedtaksperiodeBehandling(
            vedtaksperiode =
                VedtaksperiodeBehandlingDbRecord(
                    opprettetDatabase = Instant.now(),
                    oppdatertDatabase = Instant.now(),
                    sisteSpleisstatus = StatusVerdi.VENTER_PÅ_ARBEIDSGIVER,
                    sisteSpleisstatusTidspunkt = Instant.now(),
                    sisteVarslingstatus = null,
                    sisteVarslingstatusTidspunkt = null,
                    vedtaksperiodeId = UUID.randomUUID().toString(),
                    behandlingId = UUID.randomUUID().toString(),
                ),
            soknader = emptyList(),
            statuser = emptyList(),
        )
    val soknad =
        Sykepengesoknad(
            orgnummer = Testdata.orgNr,
            startSyketilfelle = LocalDate.now(),
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            fnr = Testdata.fnr,
            sendt = Instant.now(),
            opprettetDatabase = Instant.now(),
            sykepengesoknadUuid = Testdata.soknadId,
            soknadstype = "SOKNAD",
        )

    @Test
    fun `finn likeste inntektsmelding når vi treffer på id`() {
        val riktigInntektsmelding =
            InntektsmeldingDbRecord(
                id = UUID.randomUUID().toString(),
                virksomhetsnummer = Testdata.orgNr,
                foersteFravaersdag = LocalDate.now(),
                mottattDato = Instant.now(),
                vedtaksperiodeId = vedtaksperiodeBehandling.vedtaksperiode.vedtaksperiodeId,
                arbeidsgivertype = "VIRKSOMHET",
                fnr = Testdata.fnr,
                fullRefusjon = true,
                inntektsmeldingId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
            )
        val inntektsmeldinger: List<InntektsmeldingDbRecord> =
            listOf(
                riktigInntektsmelding,
                InntektsmeldingDbRecord(
                    id = UUID.randomUUID().toString(),
                    virksomhetsnummer = Testdata.orgNr,
                    foersteFravaersdag = LocalDate.now(),
                    mottattDato = Instant.now(),
                    vedtaksperiodeId = UUID.randomUUID().toString(),
                    arbeidsgivertype = "VIRKSOMHET",
                    fnr = Testdata.fnr,
                    fullRefusjon = true,
                    inntektsmeldingId = UUID.randomUUID().toString(),
                    opprettet = Instant.now(),
                ),
            ).shuffled()

        val inntektsmelding = finnLikesteInntektsmelding(inntektsmeldinger, vedtaksperiodeBehandling, soknad)
        inntektsmelding shouldBeEqualTo riktigInntektsmelding
    }

    @Test
    fun `finn likeste inntektsmelding når inntektsmeldingen ble sendt 29 dager etter sendt`() {
        val riktigInntektsmelding =
            InntektsmeldingDbRecord(
                id = UUID.randomUUID().toString(),
                virksomhetsnummer = Testdata.orgNr,
                foersteFravaersdag = soknad.startSyketilfelle.minusDays(29),
                mottattDato = Instant.now(),
                vedtaksperiodeId = null,
                arbeidsgivertype = "VIRKSOMHET",
                fnr = Testdata.fnr,
                fullRefusjon = true,
                inntektsmeldingId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
            )
        val inntektsmeldinger: List<InntektsmeldingDbRecord> =
            listOf(
                riktigInntektsmelding,
                InntektsmeldingDbRecord(
                    id = UUID.randomUUID().toString(),
                    virksomhetsnummer = Testdata.orgNr,
                    foersteFravaersdag = LocalDate.now().plusYears(1),
                    mottattDato = Instant.now(),
                    vedtaksperiodeId = null,
                    arbeidsgivertype = "VIRKSOMHET",
                    fnr = Testdata.fnr,
                    fullRefusjon = true,
                    inntektsmeldingId = UUID.randomUUID().toString(),
                    opprettet = Instant.now(),
                ),
            ).shuffled()

        val inntektsmelding = finnLikesteInntektsmelding(inntektsmeldinger, vedtaksperiodeBehandling, soknad)
        inntektsmelding shouldBeEqualTo riktigInntektsmelding
    }

    @Test
    fun `finner den nyeste mottatte  inntektsmelding når flere inntektsmeldingener ble sendt innen 29 dager etter sendt`() {
        val riktigInntektsmelding =
            InntektsmeldingDbRecord(
                id = UUID.randomUUID().toString(),
                virksomhetsnummer = Testdata.orgNr,
                foersteFravaersdag = soknad.startSyketilfelle.minusDays(29),
                mottattDato = Instant.now(),
                vedtaksperiodeId = null,
                arbeidsgivertype = "VIRKSOMHET",
                fnr = Testdata.fnr,
                fullRefusjon = true,
                inntektsmeldingId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
            )
        val denGamleInntektsmelding =
            InntektsmeldingDbRecord(
                id = UUID.randomUUID().toString(),
                virksomhetsnummer = Testdata.orgNr,
                foersteFravaersdag = soknad.startSyketilfelle.minusDays(29),
                mottattDato = OffsetDateTime.now().minusDays(1).toInstant(),
                vedtaksperiodeId = null,
                arbeidsgivertype = "VIRKSOMHET",
                fnr = Testdata.fnr,
                fullRefusjon = true,
                inntektsmeldingId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
            )
        val inntektsmeldinger: List<InntektsmeldingDbRecord> =
            listOf(
                riktigInntektsmelding,
                denGamleInntektsmelding,
            ).shuffled()

        val inntektsmelding = finnLikesteInntektsmelding(inntektsmeldinger, vedtaksperiodeBehandling, soknad)
        inntektsmelding shouldBeEqualTo riktigInntektsmelding
    }
}
