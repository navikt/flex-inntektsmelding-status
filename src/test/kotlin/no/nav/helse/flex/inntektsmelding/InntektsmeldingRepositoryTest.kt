package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.shouldBeEqualTo
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

        val (inntektsmeldingId) = inntektsmeldingRepository.save(inntektsmeldingDbRecord)
        val inntektsmeldingHentet = inntektsmeldingRepository.findByIdOrNull(inntektsmeldingId!!)!!
        inntektsmeldingHentet.fnr shouldBeEqualTo inntektsmeldingDbRecord.fnr

        val inntektsmeldingStatusDbRecord = InntektsmeldingStatusDbRecord(
            inntektsmeldingId = inntektsmeldingId,
            opprettet = now,
            status = InntektsmeldingStatus.MANGLER
        )

        val (inntektsmeldingStatusId) = inntektsmeldingStatusRepository.save(inntektsmeldingStatusDbRecord)
        val inntektsmeldingStatusHentet = inntektsmeldingStatusRepository.findByIdOrNull(inntektsmeldingStatusId!!)!!
        inntektsmeldingStatusHentet.status shouldBeEqualTo inntektsmeldingStatusDbRecord.status
    }
}
