package no.nav.helse.flex.idempotens

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.Testdata
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.skapInntektsmelding
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.tilOpprettVarselInstance
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.SpleisStatus
import no.nav.helse.flex.vedtaksperiodebehandling.VarslingStatus
import no.nav.helse.flex.vedtaksperiodebehandling.VarslingStatus.*
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ForsinketSaksbehandlingVarselIdempotensTest : FellesTestOppsett() {
    private val behandlingstatusmelding =
        Behandlingstatusmelding(
            vedtaksperiodeId = Testdata.vedtaksperiodeId,
            behandlingId = Testdata.behandlingId,
            status = Behandlingstatustype.OPPRETTET,
            tidspunkt = sendtTidspunkt,
            eksterneSøknadIder = listOf(Testdata.soknadId),
        )

    private lateinit var førsteVarselBrukervarselId: String
    private lateinit var førsteVarselMeldingId: String
    private lateinit var revarselRunde1BrukervarselId: String
    private lateinit var revarselRunde1MeldingId: String

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad`() {
        sendSoknad(Testdata.soknad)
        sendSoknad(Testdata.soknad.copy(status = SoknadsstatusDTO.SENDT))

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(Testdata.orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi venter på saksbehandler og lagrer en inntektsmelding uten full refusjon`() {
        sendBehandlingsstatusMelding(behandlingstatusmelding)
        sendBehandlingsstatusMelding(behandlingstatusmelding.copy(status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER))
        sendBehandlingsstatusMelding(behandlingstatusmelding.copy(status = Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER))

        awaitOppdatertStatus(SpleisStatus.VENTER_PÅ_SAKSBEHANDLER)

        sendInntektsmelding(
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = "123456789",
                refusjonBelopPerMnd = BigDecimal(5000),
                beregnetInntekt = BigDecimal(10000),
                vedtaksperiodeId = Testdata.vedtaksperiodeId,
            ),
        )
        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
    }

    @Test
    @Order(2)
    fun `Etter 56 dager sender vi første varsel om forsinket saksbehandling`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(56).plusMinutes(1))
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = SpleisStatus.VENTER_PÅ_SAKSBEHANDLER,
            forventetSisteVarselstatus = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
        )

        førsteVarselBrukervarselId = fangBrukervarselId()
        førsteVarselMeldingId = fangMeldingId()
    }

    @Test
    @Order(3)
    fun `Resending av det samme første varselet gir nøyaktig samme UUID-er`() {
        tilbakestillVarslingstilstand(VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE)

        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(56).plusMinutes(1))
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = SpleisStatus.VENTER_PÅ_SAKSBEHANDLER,
            forventetSisteVarselstatus = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
        )

        val resendtBrukervarselId = fangBrukervarselId()
        val resendtMeldingId = fangMeldingId()

        resendtBrukervarselId shouldBeEqualTo førsteVarselBrukervarselId
        resendtMeldingId shouldBeEqualTo førsteVarselMeldingId
    }

    @Test
    @Order(4)
    fun `Etter 88 dager sender vi revarsel om forsinket saksbehandling - runde 1`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(88))
        cronjobResultat[SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = SpleisStatus.VENTER_PÅ_SAKSBEHANDLER,
            forventetSisteVarselstatus = REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
        )

        revarselRunde1BrukervarselId = fangNyttBrukervarselIdBlantToRecords()
        revarselRunde1MeldingId = fangNyMeldingIdBlantToRecords()

        revarselRunde1BrukervarselId shouldNotBeEqualTo førsteVarselBrukervarselId
        revarselRunde1MeldingId shouldNotBeEqualTo førsteVarselMeldingId
    }

    @Test
    @Order(5)
    fun `Resending av revarsel runde 1 gir nøyaktig samme UUID-er`() {
        tilbakestillRevarsel()

        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(88))
        cronjobResultat[SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = SpleisStatus.VENTER_PÅ_SAKSBEHANDLER,
            forventetSisteVarselstatus = REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
        )

        val resendtBrukervarselId = fangNyttBrukervarselIdBlantToRecords()
        val resendtMeldingId = fangNyMeldingIdBlantToRecords()

        resendtBrukervarselId shouldBeEqualTo revarselRunde1BrukervarselId
        resendtMeldingId shouldBeEqualTo revarselRunde1MeldingId
    }

    @Test
    @Order(6)
    fun `Etter 120 dager sender vi revarsel runde 2 og den får en ny UUID`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(120))
        cronjobResultat[SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = SpleisStatus.VENTER_PÅ_SAKSBEHANDLER,
            forventetSisteVarselstatus = REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
        )

        val revarselRunde2BrukervarselId = fangNyttBrukervarselIdBlantToRecords()
        val revarselRunde2MeldingId = fangNyMeldingIdBlantToRecords()

        revarselRunde2BrukervarselId shouldNotBeEqualTo revarselRunde1BrukervarselId
        revarselRunde2BrukervarselId shouldNotBeEqualTo førsteVarselBrukervarselId
        revarselRunde2MeldingId shouldNotBeEqualTo revarselRunde1MeldingId
        revarselRunde2MeldingId shouldNotBeEqualTo førsteVarselMeldingId
    }

    private fun fangBrukervarselId(): String =
        varslingConsumer
            .ventPåRecords(1)
            .first()
            .value()
            .tilOpprettVarselInstance()
            .varselId
            .shouldNotBeNull()

    private fun fangMeldingId(): String {
        val record = meldingKafkaConsumer.ventPåRecords(1).first()
        objectMapper.readValue<MeldingKafkaDto>(record.value()).opprettMelding.shouldNotBeNull()
        return record.key()
    }

    private fun fangNyttBrukervarselIdBlantToRecords(): String =
        varslingConsumer
            .ventPåRecords(2)
            .last()
            .value()
            .tilOpprettVarselInstance()
            .varselId
            .shouldNotBeNull()

    private fun fangNyMeldingIdBlantToRecords(): String =
        meldingKafkaConsumer
            .ventPåRecords(2)
            .last()
            .key()

    private fun tilbakestillVarslingstilstand(vararg statuserSomSkalSlettes: VarslingStatus) {
        val behandling =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                Testdata.vedtaksperiodeId,
                Testdata.behandlingId,
            )!!

        val statusRaderSomSkalSlettes =
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(behandling.id!!))
                .filter { it.status in statuserSomSkalSlettes }

        vedtaksperiodeBehandlingStatusRepository.deleteAll(statusRaderSomSkalSlettes)

        vedtaksperiodeBehandlingRepository.save(
            behandling.copy(
                sisteVarslingstatus = null,
                sisteVarslingstatusTidspunkt = null,
                oppdatertDatabase = Instant.now(),
            ),
        )
    }

    private fun tilbakestillRevarsel() {
        val behandling =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                Testdata.vedtaksperiodeId,
                Testdata.behandlingId,
            )!!

        val statusRader =
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(behandling.id!!))

        vedtaksperiodeBehandlingStatusRepository.deleteAll(
            statusRader.filter { it.status == REVARSLET_VENTER_PÅ_SAKSBEHANDLER },
        )

        val forsteVarselTidspunkt =
            statusRader
                .first { it.status == VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE }
                .tidspunkt

        vedtaksperiodeBehandlingRepository.save(
            behandling.copy(
                sisteVarslingstatus = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
                sisteVarslingstatusTidspunkt = forsteVarselTidspunkt,
                oppdatertDatabase = Instant.now(),
            ),
        )
    }
}
