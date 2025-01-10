package no.nav.helse.flex.api

import jakarta.servlet.http.HttpServletRequest
import no.nav.helse.flex.auditlogging.AuditEntry
import no.nav.helse.flex.auditlogging.EventType
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HentAlleForelagteOpplysningerForPerson
import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.kafka.AuditLogProducer
import no.nav.helse.flex.util.EnvironmentToggles
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.varselutsending.VarselutsendingCronJob
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
class FlexInternalFrontendController(
    private val clientIdValidation: ClientIdValidation,
    private val hentAltForPerson: HentAltForPerson,
    private val varselutsendingCronJob: VarselutsendingCronJob,
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson,
    private val environmentToggles: EnvironmentToggles,
    private val auditLogProducer: AuditLogProducer,
) {
    data class HentVedtaksperioderPostRequest(
        val fnr: String? = null,
        val vedtaksperiodeId: String? = null,
        val sykepengesoknadId: String? = null,
    )

    data class VedtakOgInntektsmeldingerResponse(
        val vedtaksperioder: List<FullVedtaksperiodeBehandling>,
        val inntektsmeldinger: List<InntektsmeldingDbRecord>,
        val forelagteOpplysninger: List<ForelagteOpplysningerDbRecord>,
    )

    @PostMapping(
        "/api/v1/vedtak-og-inntektsmeldinger",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentVedtakOgInntektsmeldinger(
        @RequestBody req: HentVedtaksperioderPostRequest,
        request: HttpServletRequest,
    ): VedtakOgInntektsmeldingerResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        if (req.fnr != null) {
            val perioder = hentAltForPerson.hentAltForPerson(req.fnr)
            val inntektsmeldinger = inntektsmeldingRepository.findByFnrIn(listOf(req.fnr))
            val forelagte = hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(req.fnr)
            auditLogProducer.lagAuditLog(
                AuditEntry(
                    appNavn = "flex-internal",
                    utførtAv = navIdent,
                    oppslagPå = req.fnr,
                    eventType = EventType.READ,
                    forespørselTillatt = true,
                    oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                    beskrivelse = "Henter inntektsmeldinger",
                    requestUrl = URI.create(request.requestURL.toString()),
                    requestMethod = "POST",
                ),
            )
            return VedtakOgInntektsmeldingerResponse(perioder, inntektsmeldinger, forelagte)
        }
        if (req.vedtaksperiodeId != null) {
            val perioder = hentAltForPerson.hentAltForVedtaksperiode(req.vedtaksperiodeId)
            val fnr = perioder.firstOrNull()?.soknader?.firstOrNull()?.fnr
            val inntektsmeldinger =
                fnr?.let { inntektsmeldingRepository.findByFnrIn(listOf(it)) }
                    ?: return VedtakOgInntektsmeldingerResponse(emptyList(), emptyList(), emptyList())
            val forelagte = hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(fnr)
            auditLogProducer.lagAuditLog(
                AuditEntry(
                    appNavn = "flex-internal",
                    utførtAv = navIdent,
                    oppslagPå = fnr,
                    eventType = EventType.READ,
                    forespørselTillatt = true,
                    oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                    beskrivelse = "Henter inntektsmeldinger",
                    requestUrl = URI.create(request.requestURL.toString()),
                    requestMethod = "POST",
                ),
            )
            return VedtakOgInntektsmeldingerResponse(perioder, inntektsmeldinger, forelagte)
        }
        return VedtakOgInntektsmeldingerResponse(emptyList(), emptyList(), emptyList())
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
