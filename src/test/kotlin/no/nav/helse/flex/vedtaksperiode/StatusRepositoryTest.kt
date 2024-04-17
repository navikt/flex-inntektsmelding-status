package no.nav.helse.flex.vedtaksperiode

import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class StatusRepositoryTest {
    private val inntektsmelding =
        VedtaksperiodeMedStatusHistorikk(
            id = "id",
            fnr = "fnr",
            orgNr = "orgNr",
            orgNavn = "orgNavn",
            opprettet = Instant.now(),
            vedtakFom = LocalDate.now(),
            vedtakTom = LocalDate.now(),
            eksternTimestamp = Instant.now(),
            eksternId = "eksternId",
            statusHistorikk = emptyList(),
        )

    @Test
    fun `Ikke sendt melding så alt er Donet`() {
        val inntektsmelding =
            inntektsmelding.copy(
                statusHistorikk =
                    listOf(
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                    ),
            )

        inntektsmelding.alleBrukernotifikasjonerErDonet() `should be` true
    }

    @Test
    fun `Har sendt én melding som ikke er Donet`() {
        val inntektsmelding =
            inntektsmelding.copy(
                statusHistorikk =
                    listOf(
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                    ),
            )

        inntektsmelding.alleBrukernotifikasjonerErDonet() `should be` false
    }

    @Test
    fun `Har sendt én melding som er Donet`() {
        val inntektsmelding =
            inntektsmelding.copy(
                statusHistorikk =
                    listOf(
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT, Instant.now()),
                    ),
            )

        inntektsmelding.alleBrukernotifikasjonerErDonet() `should be` true
    }

    @Test
    fun `Har sendt flere meldinger hvor ikke alle er Donet`() {
        val inntektsmelding =
            inntektsmelding.copy(
                statusHistorikk =
                    listOf(
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.HAR_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                    ),
            )

        inntektsmelding.alleBrukernotifikasjonerErDonet() `should be` false
    }

    @Test
    fun `Har sendt flere meldinger og alle er Donet`() {
        val inntektsmelding =
            inntektsmelding.copy(
                statusHistorikk =
                    listOf(
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.HAR_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT, Instant.now()),
                    ),
            )

        inntektsmelding.alleBrukernotifikasjonerErDonet() `should be` true
    }

    @Test
    fun `Har sendt likt antall meldinger og Done-meldinger men ikke i riktig rekkefølge`() {
        val inntektsmelding =
            inntektsmelding.copy(
                statusHistorikk =
                    listOf(
                        StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT, Instant.now()),
                        StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT, Instant.now()),
                    ),
            )

        inntektsmelding.alleBrukernotifikasjonerErDonet() `should be` false
    }
}
