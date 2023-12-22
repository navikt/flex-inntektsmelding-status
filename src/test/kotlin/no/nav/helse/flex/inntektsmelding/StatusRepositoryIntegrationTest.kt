package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import java.time.Instant
import java.time.LocalDate
import java.util.*

class StatusRepositoryIntegrationTest : FellesTestOppsett() {
    @BeforeEach
    fun forHverTest() {
        slettFraDatabase()
    }

    @Test
    fun `Hent inntektsmeldinger med angitt status`() {
        lagInntektsmeldingMedStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
        val andreId =
            lagInntektsmeldingMedStatus(
                StatusVerdi.MANGLER_INNTEKTSMELDING,
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )

        val inntektsmeldinger =
            statusRepository.hentAlleMedNyesteStatus(
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )

        inntektsmeldinger shouldHaveSize 1
        inntektsmeldinger.first().id `should be equal to` andreId
    }

    @Test
    fun `Hent alle inntektsmeldinger med to forskjellige angitte statuser`() {
        val forsteId = lagInntektsmeldingMedStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
        val andreId =
            lagInntektsmeldingMedStatus(
                StatusVerdi.MANGLER_INNTEKTSMELDING,
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )

        val inntektsmeldinger =
            statusRepository.hentAlleMedNyesteStatus(
                StatusVerdi.MANGLER_INNTEKTSMELDING,
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )

        inntektsmeldinger shouldHaveSize 2
        inntektsmeldinger.first().id `should be equal to` forsteId
        inntektsmeldinger.drop(1).first().id `should be equal to` andreId
    }

    @Test
    fun `Hent inntektsmelding med statushistorikk før første status er lagret`() {
        val inntektsmeldingId = lagInntektsmeldingMedStatus()

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingId)
        inntektsmelding!!.id `should be equal to` inntektsmeldingId
        inntektsmelding.statusHistorikk.size `should be equal to` 0
    }

    @Test
    fun `Hent inntektsmelding med statushistorikk`() {
        val inntektsmeldingId =
            lagInntektsmeldingMedStatus(
                StatusVerdi.MANGLER_INNTEKTSMELDING,
                StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
            )

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingId)
        inntektsmelding!!.id `should be equal to` inntektsmeldingId
        inntektsmelding.statusHistorikk.size `should be equal to` 3
        inntektsmelding.statusHistorikk.first().status `should be` StatusVerdi.MANGLER_INNTEKTSMELDING
        inntektsmelding.statusHistorikk.drop(1).first().status `should be equal to`
            StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT
        inntektsmelding.statusHistorikk.drop(2).first().status `should be equal to`
            StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT
    }

    private fun lagInntektsmeldingMedStatus(vararg statuser: StatusVerdi): String {
        val localDate = LocalDate.now()
        val instant = Instant.now()
        val postfix = RandomStringUtils.randomAlphanumeric(3)

        val inntektsmelding =
            InntektsmeldingDbRecord(
                fnr = "fnr-$postfix",
                orgNr = "orgNr-$postfix",
                orgNavn = "orgNavn-$postfix",
                opprettet = instant,
                vedtakFom = localDate,
                vedtakTom = localDate.plusDays(1),
                eksternId = UUID.randomUUID().toString(),
                eksternTimestamp = instant,
            )

        val (inntektsmeldingId) = inntektsmeldingRepository.save(inntektsmelding)

        var counter = 0
        statuser.forEach {
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingId!!,
                    opprettet = (counter == 0).let { instant }.plusSeconds(counter.toLong()),
                    status = it,
                ),
            )
            counter++
        }

        return inntektsmeldingId!!
    }
}
