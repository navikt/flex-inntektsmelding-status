package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant
import java.time.LocalDate

internal class InntektsmeldingRepositoryTest : FellesTestOppsett() {

    @Test
    fun `Test Flyway-migrering`() {

        val now = Instant.now()
        val fomTom = LocalDate.now()

        val inntektsmeldingDbRecord = InntektsmeldingDbRecord(
            fnr = "fnr",
            orgNr = "orgNr",
            orgNavn = "orgNavn",
            opprettet = now,
            vedtakFom = fomTom,
            vedtakTom = fomTom,
            eksternId = "ekstern-id",
            eksternTimestamp = now.minusMillis(10000)
        )

        val (id) = inntektsmeldingRepository.save(inntektsmeldingDbRecord)

        val hentet = inntektsmeldingRepository.findByIdOrNull(id!!)!!

        hentet.fnr `should be equal to` inntektsmeldingDbRecord.fnr
    }
}
