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

internal class StatusRepositoryIntegrationTest : FellesTestOppsett() {

    @BeforeEach
    fun forHverTest() {
        slettFraDatabase()
    }

    @Test
    fun `Hent inntektsmeldinger med angitt status`() {
        lagInntektsmeldingMedStatus(StatusVerdi.MANGLER)
        val andreId = lagInntektsmeldingMedStatus(
            StatusVerdi.MANGLER,
            StatusVerdi.BRUKERNOTIFIKSJON_LUKKET
        )

        val inntektsmeldinger = statusRepository.hentAlleMedNyesteStatus(
            StatusVerdi.BRUKERNOTIFIKSJON_LUKKET
        )

        inntektsmeldinger shouldHaveSize 1
        inntektsmeldinger.first().id `should be equal to` andreId
    }

    @Test
    fun `Hent alle inntektsmeldigner med to forskjellige angitte statuser`() {
        val forsteId = lagInntektsmeldingMedStatus(StatusVerdi.MANGLER)
        val andreId = lagInntektsmeldingMedStatus(
            StatusVerdi.MANGLER,
            StatusVerdi.BRUKERNOTIFIKSJON_LUKKET
        )

        val inntektsmeldinger = statusRepository.hentAlleMedNyesteStatus(
            StatusVerdi.MANGLER,
            StatusVerdi.BRUKERNOTIFIKSJON_LUKKET
        )

        inntektsmeldinger shouldHaveSize 2
        inntektsmeldinger.first().id `should be equal to` forsteId
        inntektsmeldinger.drop(1).first().id `should be equal to` andreId
    }

    @Test
    fun `Hent inntektsmelding med statushistorikk`() {
        val inntektsmeldingId = lagInntektsmeldingMedStatus(
            StatusVerdi.MANGLER,
            StatusVerdi.BRUKERNOTIFIKSJON_DONE_SENDT,
            StatusVerdi.BRUKERNOTIFIKSJON_SENDT
        )

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingId)
        inntektsmelding!!.id `should be equal to` inntektsmeldingId
        inntektsmelding.statusHistorikk.size `should be equal to` 3
        inntektsmelding.statusHistorikk.first().status `should be` StatusVerdi.MANGLER
        inntektsmelding.statusHistorikk.drop(1).first().status `should be equal to` StatusVerdi.BRUKERNOTIFIKSJON_DONE_SENDT
        inntektsmelding.statusHistorikk.drop(2).first().status `should be equal to` StatusVerdi.BRUKERNOTIFIKSJON_SENDT
    }

    private fun lagInntektsmeldingMedStatus(vararg statuser: StatusVerdi): String {
        val localDate = LocalDate.now()
        val instant = Instant.now()
        val postfix = RandomStringUtils.randomAlphanumeric(3)

        val inntektsmelding = InntektsmeldingDbRecord(
            fnr = "fnr-$postfix",
            orgNr = "orgNr-$postfix",
            orgNavn = "orgNavn-$postfix",
            opprettet = instant,
            vedtakFom = localDate,
            vedtakTom = localDate.plusDays(1),
            eksternId = UUID.randomUUID().toString(),
            eksternTimestamp = instant
        )

        val (inntektsmeldingId) = inntektsmeldingRepository.save(inntektsmelding)

        var counter = 0
        statuser.forEach {
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = inntektsmeldingId!!,
                    opprettet = (counter == 0).let { instant }.plusSeconds(counter.toLong()),
                    status = it
                )
            )
            counter++
        }

        return inntektsmeldingId!!
    }
}