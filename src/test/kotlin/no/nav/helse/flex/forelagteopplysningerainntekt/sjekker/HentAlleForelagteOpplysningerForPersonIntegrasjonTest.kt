package no.nav.helse.flex.forelagteopplysningerainntekt.sjekker

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagtStatus
import no.nav.helse.flex.forelagteopplysningerainntekt.ForelagteOpplysningerDbRecord
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadDbRecord
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

private val ANY_INSTANT = Instant.parse("2000-01-01T00:00:00.00Z")

class HentAlleForelagteOpplysningerForPersonIntegrasjonTest : FellesTestOppsett() {
    @Autowired
    lateinit var hentAlleForelagteOpplysningerForPerson: HentAlleForelagteOpplysningerForPerson

    @AfterEach
    fun rensDb() {
        super.slettFraDatabase()
    }

    @Test
    fun `burde hente med samme fnr og orgnummer`() {
        lagreForelagteOpplysningerMedTilhorendeData(
            fnr = "test-fnr-11",
            orgnummer = "test-orgnummer",
        )
        hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(
            fnr = "test-fnr-11",
        ) shouldHaveSize 1
    }

    @Test
    fun `burde hente mange med samme fnr og orgnummer`() {
        lagreForelagteOpplysningerMedTilhorendeData(
            fnr = "test-fnr-11",
            orgnummer = "test-orgnummer",
        )
        lagreForelagteOpplysningerMedTilhorendeData(
            fnr = "test-fnr-11",
            orgnummer = "test-orgnummer",
        )
        hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(
            fnr = "test-fnr-11",
        ) shouldHaveSize 2
    }

    @Test
    fun `burde ikke hente data med annet fnr`() {
        lagreForelagteOpplysningerMedTilhorendeData(
            fnr = "test-fnr-11",
            orgnummer = "test-orgnummer",
        )
        hentAlleForelagteOpplysningerForPerson.hentAlleForelagteOpplysningerFor(
            fnr = "annet-fnr-1",
        ) shouldHaveSize 0
    }

    private fun lagreForelagteOpplysningerMedTilhorendeData(
        sykepengesoknadUuid: String = UUID.randomUUID().toString(),
        fnr: String = "test-fnr-11",
        orgnummer: String = "test-org",
        forelagt: Instant? = ANY_INSTANT,
    ) {
        val vedtaksperiodeId: String = UUID.randomUUID().toString()
        val behandlingId: String = UUID.randomUUID().toString()

        ForelagteOpplysningerDbRecord(
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            forelagteOpplysningerMelding =
                PGobject().apply {
                    type = "json"
                    value = "{}"
                },
            opprettet = ANY_INSTANT,
            statusEndret = forelagt,
            status = ForelagtStatus.SKAL_FORELEGGES,
            opprinneligOpprettet = ANY_INSTANT,
        ).also {
            forelagteOpplysningerRepository.save(it)
        }

        val soknad =
            Sykepengesoknad(
                sykepengesoknadUuid = sykepengesoknadUuid,
                orgnummer = orgnummer,
                soknadstype = "ARBEIDSTAKER",
                startSyketilfelle = LocalDate.of(2024, 1, 1),
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
    }
}
