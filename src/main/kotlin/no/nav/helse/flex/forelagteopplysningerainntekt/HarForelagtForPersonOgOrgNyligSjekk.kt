package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class HarForelagtForPersonMedOrgNyligSjekk(
    private val hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson,
) {
    private val log = logger()

    fun sjekk(
        fnr: String,
        orgnummer: String,
        now: Instant,
    ): Boolean {
        val forrigeForeleggelse = finnForrigeForeleggelseFor(fnr, orgnummer)
        val sjekkForelagtEtter = now.minus(Duration.ofDays(28))
        if (forrigeForeleggelse != null &&
            forrigeForeleggelse.forelagt!!.isAfter(sjekkForelagtEtter)
        ) {
            val dagerSidenForeleggelse = ChronoUnit.DAYS.between(forrigeForeleggelse.forelagt, now)
            log.warn(
                "Person har blitt forelagt nylig, for $dagerSidenForeleggelse dager siden. " +
                    "Forrige forelagte opplysning id: ${forrigeForeleggelse.id}",
            )
            return false
        } else {
            return true
        }
    }

    private fun finnForrigeForeleggelseFor(
        fnr: String,
        orgnummer: String,
    ): ForelagteOpplysningerDbRecord? {
        val forelagteOpplysninger =
            hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(fnr = fnr, orgnr = orgnummer)
        val sisteForeleggelse =
            forelagteOpplysninger
                .filter { it.forelagt != null }
                .maxByOrNull { it.forelagt!! }
        return sisteForeleggelse
    }
}

@Component
class HentAlleForelagteOpplysningerForPerson(
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
) {
    internal fun hentAlleForelagteOpplysningerFor(
        fnr: String,
        orgnr: String,
    ): List<ForelagteOpplysningerDbRecord> {
        val sykepengesoknader =
            sykepengesoknadRepository.findByFnr(fnr)
                .filter { it.orgnummer == orgnr }
        val sykepengesoknadUuids = sykepengesoknader.map { it.sykepengesoknadUuid }
        val relasjoner =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(sykepengesoknadUuids)
        val vedtaksperiodeBehandlinger =
            relasjoner.map {
                vedtaksperiodeBehandlingRepository.findById(it.vedtaksperiodeBehandlingId).get()
            }
        return vedtaksperiodeBehandlinger.flatMap {
            forelagteOpplysningerRepository.findAllByVedtaksperiodeIdAndBehandlingId(
                it.vedtaksperiodeId,
                it.behandlingId,
            )
        }
    }
}
