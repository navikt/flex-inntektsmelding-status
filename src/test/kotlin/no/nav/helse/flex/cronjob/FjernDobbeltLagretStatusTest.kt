package no.nav.helse.flex.cronjob

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.inntektsmelding.*
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

class FjernDobbeltLagretStatusTest : FellesTestOppsett() {
    @Autowired
    lateinit var fjernDobbeltLagretStatus: FjernDobbeltLagretStatus

    private val inntektsmelding = InntektsmeldingMedStatusHistorikk(
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
    fun `sletter alle duplikat manglerinntektsmeldingstatus`() {
        val inntektsmelding = inntektsmelding.copy(
            statusHistorikk = listOf(
                StatusHistorikk("id", StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING),
                StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING),
                StatusHistorikk("id", StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT),
                StatusHistorikk("id", StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT),
                StatusHistorikk("id", StatusVerdi.MANGLER_INNTEKTSMELDING),
            )
        )

        val id = inntektsmeldingRepository.save(
            InntektsmeldingDbRecord(
                fnr = inntektsmelding.fnr,
                orgNr = inntektsmelding.orgNr,
                orgNavn = inntektsmelding.orgNavn,
                opprettet = Instant.now(),
                vedtakFom = inntektsmelding.vedtakFom,
                vedtakTom = inntektsmelding.vedtakTom,
                eksternId = inntektsmelding.eksternId,
                eksternTimestamp = inntektsmelding.eksternTimestamp
            )
        ).id!!

        inntektsmeldingStatusRepository.saveAll(
            inntektsmelding.statusHistorikk.map {
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = id,
                    opprettet = Instant.now(),
                    status = it.status
                )
            }
        )

        fjernDobbeltLagretStatus.fjernDuplikatInntektsmeldingstatus()

        statusRepository.hentInntektsmeldingMedStatusHistorikk(id)!!.statusHistorikk.map {
            it.status
        } `should be equal to` listOf(
            StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING,
            StatusVerdi.MANGLER_INNTEKTSMELDING,
            StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
            StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
        )
    }
}
