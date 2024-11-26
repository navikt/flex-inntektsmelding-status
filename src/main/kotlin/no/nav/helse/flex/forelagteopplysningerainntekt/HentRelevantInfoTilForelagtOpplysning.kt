package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.logger
import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

data class RelevantInfoTilForelagtOpplysning(
    val fnr: String,
    val startSyketilfelle: LocalDate,
    val orgnummer: String,
    val orgNavn: String,
)

@Component
class HentRelevantInfoTilForelagtOpplysning(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val organisasjonRepository: OrganisasjonRepository,
) {
    val log = logger()

    fun hentRelevantInfoFor(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): RelevantInfoTilForelagtOpplysning? {
        val sykepengeSoknader =
            finnSykepengesoknader(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
            )

        val debugId = "vedtaksperiodeId: $vedtaksperiodeId, behandlingId: $behandlingId"
        if (sykepengeSoknader.isEmpty()) {
            log.warn("Finnes ingen sykepengesoknader relatert til forelagte opplysninger. vedtaksperiodeId: $debugId")
            return null
        }
        if (sykepengeSoknader.size > 1) {
            log.warn(
                "Fant flere sykepengesoknader for forelagte opplysninger: $debugId. " +
                    "Vi baserer fnr og orgnr på den siste",
            )
        }
        val sisteSykepengeSoknad = sykepengeSoknader.maxBy { it.tom }

        if (sisteSykepengeSoknad.orgnummer == null) {
            log.warn("Siste sykepengesøknad for forelagte opplysninger inneholder ikke orgnummer: $debugId")
            return null
        }

        val org = organisasjonRepository.findByOrgnummer(sisteSykepengeSoknad.orgnummer)
        if (org == null) {
            log.warn(
                "Organisasjon for forelagte opplysninger finnes ikke. Forelagte opplysninger: " +
                    "$debugId, orgnummer: ${sisteSykepengeSoknad.orgnummer}",
            )
            return null
        }

        return RelevantInfoTilForelagtOpplysning(
            fnr = sisteSykepengeSoknad.fnr,
            orgnummer = sisteSykepengeSoknad.orgnummer,
            startSyketilfelle = sisteSykepengeSoknad.startSyketilfelle,
            orgNavn = org.navn,
        )
    }

    private fun finnSykepengesoknader(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): List<Sykepengesoknad> {
        val vedtaksperiodeBehandlingId =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
            )?.id

        val relevanteVedtaksperiodebehandlingSykepengesoknaderRelations =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingId(
                vedtaksperiodeBehandlingId ?: "",
            )

        val relevanteSykepengesoknader =
            sykepengesoknadRepository.findBySykepengesoknadUuidIn(
                relevanteVedtaksperiodebehandlingSykepengesoknaderRelations.map {
                    it.sykepengesoknadUuid
                },
            )

        return relevanteSykepengesoknader
    }
}
