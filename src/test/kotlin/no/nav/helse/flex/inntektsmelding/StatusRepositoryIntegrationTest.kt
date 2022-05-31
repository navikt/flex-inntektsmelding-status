package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
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
        lagInntektsmeldingMedStatus(InntektsmeldingStatus.MANGLER)
        val andreId = lagInntektsmeldingMedStatus(
            InntektsmeldingStatus.MANGLER,
            InntektsmeldingStatus.BRUKERNOTIFIKSJON_LUKKET
        )

        val inntektsmeldinger = statusRepository.hentAlleMedNyesteStatus(
            InntektsmeldingStatus.BRUKERNOTIFIKSJON_LUKKET
        )

        inntektsmeldinger shouldHaveSize 1
        inntektsmeldinger.first().id `should be equal to` andreId
    }

    @Test
    fun `Hent alle inntektsmeldigner med to forskjellige angitte statuser`() {
        val forsteId = lagInntektsmeldingMedStatus(InntektsmeldingStatus.MANGLER)
        val andreId = lagInntektsmeldingMedStatus(
            InntektsmeldingStatus.MANGLER,
            InntektsmeldingStatus.BRUKERNOTIFIKSJON_LUKKET
        )

        val inntektsmeldinger = statusRepository.hentAlleMedNyesteStatus(
            InntektsmeldingStatus.MANGLER,
            InntektsmeldingStatus.BRUKERNOTIFIKSJON_LUKKET
        )

        inntektsmeldinger shouldHaveSize 2
        inntektsmeldinger.first().id `should be equal to` forsteId
        inntektsmeldinger.drop(1).first().id `should be equal to` andreId
    }

    private fun lagInntektsmeldingMedStatus(vararg statuser: InntektsmeldingStatus): String {
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
