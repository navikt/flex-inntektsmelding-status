package no.nav.helse.flex.vedtaksperiode

import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InntektsmeldingMedStatusOverlapperExtKtTest {
    val fom = LocalDate.now()
    val tom = fom.plusDays(2)
    val base =
        VedtaksperiodeMedStatus(
            id = UUID.randomUUID().toString(),
            fnr = "123",
            orgNr = "sdf",
            orgNavn = "sdgsdfs",
            vedtakFom = fom,
            vedtakTom = tom,
            eksternTimestamp = Instant.now(),
            eksternId = "321423",
            status = StatusVerdi.MANGLER_INNTEKTSMELDING,
            statusOpprettet = Instant.now(),
            opprettet = Instant.now(),
        )

    @Test
    fun `tom liste overlapper ikke`() {
        emptyList<VedtaksperiodeMedStatus>().overlapper().`should be false`()
    }

    @Test
    fun `en i liste overlapper ikke`() {
        listOf(base).overlapper().`should be false`()
    }

    @Test
    fun `duplikat i liste overlapper ikke`() {
        listOf(base, base).overlapper().`should be false`()
    }

    @Test
    fun `to som ikke overlapper`() {
        val neste =
            base.copy(
                vedtakFom = tom.plusDays(1),
                vedtakTom = tom.plusDays(3),
            )
        listOf(base, neste).overlapper().`should be false`()
    }

    @Test
    fun `to som overlapper`() {
        val neste =
            base.copy(
                vedtakFom = tom,
                vedtakTom = tom.plusDays(3),
            )
        listOf(base, neste).overlapper().`should be true`()
    }

    @Test
    fun `ignorerer at vi overlapper med behandling utafor spleis`() {
        val neste =
            base.copy(
                vedtakFom = tom,
                vedtakTom = tom.plusDays(3),
                status = StatusVerdi.BEHANDLES_UTENFOR_SPLEIS,
            )
        listOf(base, neste).overlapper().`should be false`()
    }

    @Test
    fun `tester behandles utafor overlapper mangler inntektsmelding`() {
        val neste =
            base.copy(
                vedtakFom = tom,
                vedtakTom = tom.plusDays(3),
                status = StatusVerdi.BEHANDLES_UTENFOR_SPLEIS,
            )
        listOf(base, neste).manglendeInntektsmeldingOverlapperBehandlesUtaforSpleis().`should be true`()
    }
}
