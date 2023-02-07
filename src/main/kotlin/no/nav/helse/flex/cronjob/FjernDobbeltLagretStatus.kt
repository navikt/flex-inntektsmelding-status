package no.nav.helse.flex.cronjob

import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class FjernDobbeltLagretStatus(
    private val statusRepository: StatusRepository
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
                    StatusVerdi.MANGLER_INNTEKTSMELDING,
                    StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                    StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
                    StatusVerdi.MANGLER_INNTEKTSMELDING
                )
            ) {
                statusRepository.slettduplikatStatus(inntektsmeldingMedStatusHistorikk.statusHistorikk.first().id)
            }
        }
    }
}
