package no.nav.helse.flex.api

import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.varselutsending.VarselutsendingCronJob
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
class FlexInternalFrontendController(
    private val clientIdValidation: ClientIdValidation,
    private val hentAltForPerson: HentAltForPerson,
    private val varselutsendingCronJob: VarselutsendingCronJob,
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val environmentToggles: EnvironmentToggles,
) {
    @GetMapping("/api/v1/vedtaksperioder", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentVedtaksperioder(
        @RequestHeader fnr: String,
    ): List<FullVedtaksperiodeBehandling> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return hentAltForPerson.hentAltForPerson(fnr)
    }

    data class HentVedtaksperioderPostRequest(
        val fnr: String? = null,
        val vedtaksperiodeId: String? = null,
        val sykepengesoknadId: String? = null,
    )

    data class VedtakOgInntektsmeldingerResponse(
        val vedtaksperioder: List<FullVedtaksperiodeBehandling>,
        val inntektsmeldinger: List<InntektsmeldingDbRecord>,
    )

    @PostMapping(
        "/api/v1/vedtaksperioder",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentVedtaksperioderPost(
        @RequestBody req: HentVedtaksperioderPostRequest,
    ): List<FullVedtaksperiodeBehandling> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        if (req.fnr != null) {
            return hentAltForPerson.hentAltForPerson(req.fnr)
        }
        if (req.vedtaksperiodeId != null) {
            return hentAltForPerson.hentAltForVedtaksperiode(req.vedtaksperiodeId)
        }
        return emptyList()
    }

    @PostMapping(
        "/api/v1/vedtak-og-inntektsmeldinger",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentVedtakOgInntektsmeldinger(
        @RequestBody req: HentVedtaksperioderPostRequest,
    ): VedtakOgInntektsmeldingerResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        if (req.fnr != null) {
            val perioder = hentAltForPerson.hentAltForPerson(req.fnr)
            val inntektsmeldinger = inntektsmeldingRepository.findByFnrIn(listOf(req.fnr))
            return VedtakOgInntektsmeldingerResponse(perioder, inntektsmeldinger)
        }
        if (req.vedtaksperiodeId != null) {
            val perioder = hentAltForPerson.hentAltForVedtaksperiode(req.vedtaksperiodeId)
            val fnr = perioder.firstOrNull()?.soknader?.firstOrNull()?.fnr
            val inntektsmeldinger = fnr ?.let { inntektsmeldingRepository.findByFnrIn(listOf(it)) } ?: emptyList()
            return VedtakOgInntektsmeldingerResponse(perioder, inntektsmeldinger)
        }
        return VedtakOgInntektsmeldingerResponse(emptyList(), emptyList())
    }

    @GetMapping("/api/v1/inntektsmeldinger", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentInntektsmedlinger(
        @RequestHeader fnr: String,
    ): List<InntektsmeldingDbRecord> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return inntektsmeldingRepository.findByFnrIn(listOf(fnr))
    }

    @PostMapping("/api/v1/cronjob", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun startCronjob(
        @RequestParam now: Instant,
    ): Map<String, String> {
        if (environmentToggles.isProduction()) {
            throw IllegalStateException("Kan ikke kjøre cronjob manuelt i prod")
        }
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return varselutsendingCronJob
            .runMedParameter(now.tilOsloZone())
            .map { it.key.name to it.value.toString() }
            .toMap().toMutableMap().also { it["now"] = now.tilOsloZone().toString() }
    }
}
