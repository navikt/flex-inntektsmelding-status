package no.nav.helse.flex.opprydning

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtaksperiode.StatusRepository
import no.nav.helse.flex.vedtaksperiode.StatusVerdi
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class OpprydningJobb(
    private val statusRepository: StatusRepository,
    private val opprydning: Opprydning,
) {
    private val log = logger()

    @Scheduled(initialDelay = 2, fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    fun job() {
        jobMedParameter()
    }

    fun jobMedParameter() {
        val manglerImSendt =
            statusRepository
                .hentAlleMedNyesteStatus(StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT)

        log.info("Fant ${manglerImSendt.size} perioder med siste status DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT")

        var oppryddet = 0

        manglerImSendt
            .take(200).forEach {
                opprydning.fjernVarsler(it)
                oppryddet++
            }

        if (oppryddet > 0) {
            log.info("Behandlet $oppryddet antall perioder med siste status DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT")
        } else {
            log.info("Ingen perioder med siste status DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT ble behandlet")
        }
    }
}
