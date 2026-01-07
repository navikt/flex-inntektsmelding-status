package no.nav.helse.flex.testdata

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.inntektsmelding.InntektsmeldingDbRecord
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadDbRecord
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestdataResetListenerTest : FellesTestOppsett() {
    private val orgnummer = "999999999"

    @BeforeEach
    fun slettTestData() {
        slettFraDatabase()
    }

    @Test
    fun `Slett inntektsmeldinger`() {
        inntektsmeldingRepository.save(
            InntektsmeldingDbRecord(
                inntektsmeldingId = "inntektsmelding-1",
                fnr = "11111111111",
                arbeidsgivertype = "VIRKSOMHET",
                virksomhetsnummer = "888888888",
                fullRefusjon = true,
                opprettet = Instant.now(),
                mottattDato = Instant.now(),
                foersteFravaersdag = LocalDate.now(),
                vedtaksperiodeId = null,
            ),
        )

        inntektsmeldingRepository.save(
            InntektsmeldingDbRecord(
                inntektsmeldingId = "inntektsmelding-2",
                fnr = "22222222222",
                arbeidsgivertype = "VIRKSOMHET",
                virksomhetsnummer = "999999999",
                fullRefusjon = true,
                opprettet = Instant.now(),
                mottattDato = Instant.now(),
                foersteFravaersdag = LocalDate.now(),
                vedtaksperiodeId = null,
            ),
        )

        sendtSlettTestdataResetMelding("11111111111")

        inntektsmeldingRepository.findByFnr("11111111111").size `should be equal to` 0
        inntektsmeldingRepository.findByFnr("22222222222").size `should be equal to` 1
    }

    @Test
    fun `Slett vedtaksperiode behandlinger og relaterte statues og søknader`() {
        lagreVedtaksperioderForBruker("11111111111")
        lagreVedtaksperioderForBruker("22222222222")

        sendtSlettTestdataResetMelding("11111111111")

        vedtaksperioderFinnesForBruker(testdataService.finnVedtaksperioderSomSkalSlettes("11111111111")) `should be` false
        vedtaksperioderFinnesForBruker(testdataService.finnVedtaksperioderSomSkalSlettes("22222222222")) `should be` true
    }

    private fun lagreVedtaksperioderForBruker(fnr: String) {
        val vedtaksperiodeId = UUID.randomUUID().toString()
        val behandlingId = UUID.randomUUID().toString()
        val soknadUuid = UUID.randomUUID().toString()

        val lagretSoknad =
            sykepengesoknadRepository.save(
                Sykepengesoknad(
                    sykepengesoknadUuid = soknadUuid,
                    orgnummer = orgnummer,
                    soknadstype = "ARBEIDSTAKERE",
                    startSyketilfelle = LocalDate.now().minusDays(10),
                    fom = LocalDate.now().minusDays(7),
                    tom = LocalDate.now(),
                    fnr = fnr,
                    sendt = Instant.now(),
                    opprettetDatabase = Instant.now(),
                ),
            )

        val lagretBehandling =
            vedtaksperiodeBehandlingRepository.save(
                VedtaksperiodeBehandlingDbRecord(
                    opprettetDatabase = Instant.now(),
                    oppdatertDatabase = Instant.now(),
                    sisteSpleisstatus = StatusVerdi.VENTER_PÅ_SAKSBEHANDLER,
                    sisteSpleisstatusTidspunkt = Instant.now(),
                    sisteVarslingstatus = null,
                    sisteVarslingstatusTidspunkt = null,
                    vedtaksperiodeId = vedtaksperiodeId,
                    behandlingId = behandlingId,
                ),
            )
        vedtaksperiodeBehandlingStatusRepository.save(
            VedtaksperiodeBehandlingStatusDbRecord(
                vedtaksperiodeBehandlingId = lagretBehandling.id!!,
                opprettetDatabase = Instant.now().minusSeconds(10),
                tidspunkt = Instant.now().minusSeconds(10),
                status = StatusVerdi.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
                brukervarselId = null,
                dittSykefravaerMeldingId = null,
            ),
        )
        vedtaksperiodeBehandlingStatusRepository.save(
            VedtaksperiodeBehandlingStatusDbRecord(
                vedtaksperiodeBehandlingId = lagretBehandling.id,
                opprettetDatabase = Instant.now(),
                tidspunkt = Instant.now(),
                status = StatusVerdi.VENTER_PÅ_SAKSBEHANDLER,
                brukervarselId = null,
                dittSykefravaerMeldingId = null,
            ),
        )
        vedtaksperiodeBehandlingSykepengesoknadRepository.save(
            VedtaksperiodeBehandlingSykepengesoknadDbRecord(
                vedtaksperiodeBehandlingId = lagretBehandling.id,
                sykepengesoknadUuid = lagretSoknad.sykepengesoknadUuid,
            ),
        )
    }

    fun sendtSlettTestdataResetMelding(fnr: String) {
        val key = UUID.randomUUID().toString()
        kafkaProducer.send(ProducerRecord(TESTDATA_RESET_TOPIC, key, fnr)).get()

        testdataResetConsumer.ventPåRecords(1).single().also {
            it.key() `should be equal to` key
            it.value() `should be equal to` "11111111111"
        }
    }

    private fun vedtaksperioderFinnesForBruker(vedtaksperioderSomSkalSlettes: VedtaksperioderSomSkalSlettes) =
        vedtaksperioderSomSkalSlettes.soknadUuidListe.isNotEmpty() ||
            vedtaksperioderSomSkalSlettes.vedtaksperiodeSoknadKoblingIdListe.isNotEmpty() ||
            vedtaksperioderSomSkalSlettes.vedtaksperiodeBehandlingerIdListe.isNotEmpty() ||
            vedtaksperioderSomSkalSlettes.behandlingStatusIdListe.isNotEmpty()
}
