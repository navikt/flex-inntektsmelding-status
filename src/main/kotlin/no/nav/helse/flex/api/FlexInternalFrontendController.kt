package no.nav.helse.flex.api

import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.vedtaksperiode.StatusRepository
import no.nav.helse.flex.vedtaksperiode.VedtaksperiodeMedStatusHistorikk
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
class FlexInternalFrontendController(
    private val clientIdValidation: ClientIdValidation,
    private val statusRepository: StatusRepository,
) {
    @GetMapping("/api/v1/vedtaksperioder", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentVedtaksperioder(
        @RequestHeader fnr: String,
    ): List<VedtaksperiodeResponse> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return statusRepository.hentAlleOrgnrForPerson(fnr)
            .map { Pair(it, statusRepository.hentAlleForPerson(fnr, it)) }
            .map {
                VedtaksperiodeResponse(
                    it.first,
                    it.second.mapNotNull { p -> statusRepository.hentVedtaksperiodeMedStatusHistorikk(p.vedtaksperiodeDbId) },
                )
            }
    }
}

data class VedtaksperiodeResponse(
    val orgnr: String,
    val statusHistorikk: List<VedtaksperiodeMedStatusHistorikk>,
)
