package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.organisasjon.Organisasjon
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadDbRecord
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

class HentRelevantInfoTilForelagteOpplysningerIntegrasjonTest : FellesTestOppsett() {
    @Autowired
    lateinit var hentRelevantInfoTilForelagtOpplysning: HentRelevantInfoTilForelagtOpplysning

    @AfterEach
    fun rensDb() {
        super.slettFraDatabase()
    }

    @Test
    fun `burde hente info med riktig vedtaksperiodeId og behandlingId`() {
        lagreSykepengesoknad(
            vedtaksperiodeId = "v-id",
            behandlingId = "b-id",
        )

        hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
            vedtaksperiodeId = "v-id",
            behandlingId = "b-id",
        ).`should not be null`()
    }

    @Test
    fun `burde ikke hente info med annen vedtaksperiodeId`() {
        lagreSykepengesoknad(
            vedtaksperiodeId = "v-id",
            behandlingId = "b-id",
        )

        hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
            vedtaksperiodeId = "annen-v-id",
            behandlingId = "b-id",
        ).`should be null`()
    }

    @Test
    fun `burde ikke hente info med annen behandlingId`() {
        lagreSykepengesoknad(
            vedtaksperiodeId = "v-id",
            behandlingId = "b-id",
        )

        hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
            vedtaksperiodeId = "v-id",
            behandlingId = "annen-b-id",
        ).`should be null`()
    }

    @Test
    fun `burde hente riktig relevant info`() {
        lagreSykepengesoknad(
            vedtaksperiodeId = "v-id",
            behandlingId = "b-id",
            orgnummer = "orgnummer",
            fnr = "fnr",
            orgNavn = "Org Navn",
            startSyketilfelle = LocalDate.parse("2000-01-01"),
        )

        hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
            vedtaksperiodeId = "v-id",
            behandlingId = "b-id",
        ) `should be equal to`
            RelevantInfoTilForelagtOpplysning(
                fnr = "fnr",
                orgnummer = "orgnummer",
                orgNavn = "Org Navn",
                startSyketilfelle = LocalDate.parse("2000-01-01"),
            )
    }

    private fun lagreSykepengesoknad(
        sykepengesoknadUuid: String = UUID.randomUUID().toString(),
        vedtaksperiodeId: String = "vedtaksperiode-test-opplysning",
        behandlingId: String = "behandling-test-opplysning",
        fnr: String = "testFnr0000",
        orgnummer: String = "test-org",
        orgNavn: String = "Org Navn",
        startSyketilfelle: LocalDate = LocalDate.parse("2000-01-01"),
    ) {
        val soknad =
            Sykepengesoknad(
                sykepengesoknadUuid = sykepengesoknadUuid,
                orgnummer = orgnummer,
                soknadstype = "ARBEIDSTAKER",
                startSyketilfelle = startSyketilfelle,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 16),
                fnr = fnr,
                sendt = Instant.parse("2024-01-16T00:00:00.00Z"),
                opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
            ).also {
                sykepengesoknadRepository.save(it)
            }

        val vedtaksperiodeBehandling =
            vedtaksperiodeBehandlingRepository.save(
                VedtaksperiodeBehandlingDbRecord(
                    opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    oppdatertDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteSpleisstatus = StatusVerdi.VENTER_PÅ_ARBEIDSGIVER,
                    sisteSpleisstatusTidspunkt = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteVarslingstatus = null,
                    sisteVarslingstatusTidspunkt = null,
                    vedtaksperiodeId = vedtaksperiodeId,
                    behandlingId = behandlingId,
                ),
            )

        vedtaksperiodeBehandlingSykepengesoknadRepository.save(
            VedtaksperiodeBehandlingSykepengesoknadDbRecord(
                vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id!!,
                sykepengesoknadUuid = soknad.sykepengesoknadUuid,
            ),
        )

        Organisasjon(
            orgnummer = orgnummer,
            navn = orgNavn,
            opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            oppdatert = Instant.parse("2024-01-01T00:00:00.00Z"),
            oppdatertAv = "personen",
        ).also {
            if (organisasjonRepository.findByOrgnummer(orgnummer) == null) {
                organisasjonRepository.save(it)
            }
        }
    }
}
