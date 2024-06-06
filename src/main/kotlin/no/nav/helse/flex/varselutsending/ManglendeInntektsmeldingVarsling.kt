package no.nav.helse.flex.varselutsending

import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ManglendeInntektsmeldingVarsling(
    private val hentAltForPerson: HentAltForPerson,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun prosseserManglendeInntektsmeldingKandidat(
        fnr: String,
        sendtFoer: Instant,
    ): String {
        val hent = hentAltForPerson.hentAltForPerson(fnr)
        // TODO implementer varsling
        hent.toString()
        return "sendt"
    }
}
