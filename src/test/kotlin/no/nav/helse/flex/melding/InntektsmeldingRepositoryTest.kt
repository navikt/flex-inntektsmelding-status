package no.nav.helse.flex.melding

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

        val inntektsmelding = Inntektsmelding(
            fnr = "fnr",
            orgNr = "orgNr",
            orgNavn = "orgNavn",
            opprettet = now,
            vedtakFom = fomTom,
            vedtakTom = fomTom,
            eksternId = "ekstern-id",
            eksternTimestamp = now.minusMillis(10000)
        )

        val (id) = inntektsmeldingRepository.save(inntektsmelding)

        val hentet = inntektsmeldingRepository.findByIdOrNull(id!!)

        hentet!!.id `should be equal to` id
    }
}



