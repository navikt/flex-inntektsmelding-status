package no.nav.helse.flex.cronjob

import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusRepository
import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class FjernDobbeltLagretStatus(
    private val statusRepository: StatusRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository
) {

    @Scheduled(initialDelay = 2, fixedDelay = 120, timeUnit = TimeUnit.MINUTES)
    fun job() {
        fjernDuplikatInntektsmeldingstatus()
    }

    fun fjernDuplikatInntektsmeldingstatus() {

        val sisteStatusManglerInntektsmelding =
            statusRepository.hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING).map {
                statusRepository.hentInntektsmeldingMedStatusHistorikk(it.id)!!
            }

        sisteStatusManglerInntektsmelding.forEach { inntektsmeldingMedStatusHistorikk ->
            val statusVerdier = inntektsmeldingMedStatusHistorikk.statusHistorikk.map { it.status }
            if (statusVerdier == listOf(
                    StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                    StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
                    StatusVerdi.MANGLER_INNTEKTSMELDING
                )
            ) {
                val brukernot = inntektsmeldingMedStatusHistorikk.statusHistorikk.first()
                val brukernotStatus = inntektsmeldingStatusRepository.findById(brukernot.id).get()
                val mangler = inntektsmeldingMedStatusHistorikk.statusHistorikk.last()
                val manglerStaus = inntektsmeldingStatusRepository.findById(mangler.id).get()

                require(brukernotStatus.opprettet.isBefore(manglerStaus.opprettet))

                inntektsmeldingStatusRepository.save(
                    InntektsmeldingStatusDbRecord(
                        inntektsmeldingId = inntektsmeldingMedStatusHistorikk.id,
                        opprettet = brukernotStatus.opprettet.minusSeconds(Duration.ofDays(29).toSeconds()),
                        status = StatusVerdi.MANGLER_INNTEKTSMELDING
                    )
                )

                statusRepository.slettduplikatStatus(mangler.id)
            }
        }
    }
}
