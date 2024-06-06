package no.nav.helse.flex.api

import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.varselutsending.VarselutsendingCronJob
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
class FlexInternalFrontendController(
    private val clientIdValidation: ClientIdValidation,
    private val hentAltForPerson: HentAltForPerson,
    private val varselutsendingCronJob: VarselutsendingCronJob,
    private val inntektsmeldingRepository: InntektsmeldingRepository,
) {
    @GetMapping("/api/v1/vedtaksperioder", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentVedtaksperioder(
        @RequestHeader fnr: String,
    ): List<FullVedtaksperiodeBehandling> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return hentAltForPerson.hentAltForPerson(fnr)
    }

    @GetMapping("/api/v1/inntektsmeldinger", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentInntektsmedlinger(
        @RequestHeader fnr: String,
    ): List<InntektsmeldingDbRecord> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return inntektsmeldingRepository.findByFnrIn(listOf(fnr))
    }

    @PostMapping("/api/v1/cronjob", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun startCronjob(): HashMap<String, Int> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return varselutsendingCronJob.run()
    }
}
