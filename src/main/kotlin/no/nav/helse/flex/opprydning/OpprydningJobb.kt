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
) {
    private val log = logger()

    @Scheduled(initialDelay = 2, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun job() {
        jobMedParameter()
    }

    fun jobMedParameter() {
        val manglerImSendt =
            statusRepository
                .hentAlleMedNyesteStatus(StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT)

        log.info("Fant ${manglerImSendt.size} perioder med siste status DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT")
    }
}
