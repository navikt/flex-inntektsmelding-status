package no.nav.helse.flex.cronjob

import no.nav.helse.flex.inntektsmelding.InntektsmeldingStatusRepository
import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class FjernDobbeltLagretStatus(
    private val statusRepository: StatusRepository,
    private val inntektsmeldingStatusRepository: InntektsmeldingStatusRepository
) {

    @Scheduled(initialDelay = 1, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun job() {
        fjernDuplikatInntektsmeldingstatus()
    }

    fun fjernDuplikatInntektsmeldingstatus() {

        val sisteStatusManglerInntektsmelding = statusRepository
            .hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .map { statusRepository.hentInntektsmeldingMedStatusHistorikk(it.id)!! }

        sisteStatusManglerInntektsmelding.forEach { inntektsmeldingMedStatusHistorikk ->
            val statusVerdier = inntektsmeldingMedStatusHistorikk.statusHistorikk.map { it.status }
            val sisteTreStatuser = statusVerdier.takeLast(3)
            if (sisteTreStatuser == listOf(
                    StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                    StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
                    StatusVerdi.MANGLER_INNTEKTSMELDING,
                )
            ) {
                val mangler = inntektsmeldingMedStatusHistorikk.statusHistorikk.last()
                val manglerStaus = inntektsmeldingStatusRepository.findById(mangler.id).get()
                val nestSiste = inntektsmeldingMedStatusHistorikk.statusHistorikk.last { it.id != mangler.id }
                val nestSisteStatus = inntektsmeldingStatusRepository.findById(nestSiste.id).get()

                require(nestSisteStatus.opprettet.isBefore(manglerStaus.opprettet))

                statusRepository.slettduplikatStatus(mangler.id)
            }
        }
    }
}
