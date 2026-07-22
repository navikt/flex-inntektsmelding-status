package no.nav.helse.flex.idempotens

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.Testdata
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.tilOpprettVarselInstance
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ManglendeInntektsmeldingVarselIdempotensTest : FellesTestOppsett() {
    private val behandlingstatusmelding =
        Behandlingstatusmelding(
            vedtaksperiodeId = Testdata.vedtaksperiodeId,
            behandlingId = Testdata.behandlingId,
            status = Behandlingstatustype.OPPRETTET,
            tidspunkt = sendtTidspunkt,
            eksterneSøknadIder = listOf(Testdata.soknadId),
        )

    private lateinit var forsteVarselBrukervarselId: String
    private lateinit var forsteVarselMeldingId: String
    private lateinit var andreVarselBrukervarselId: String
    private lateinit var andreVarselMeldingId: String

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesoknad og vi venter paa arbeidsgiver`() {
        sendSoknad(Testdata.soknad)
        sendSoknad(Testdata.soknad.copy(status = SoknadsstatusDTO.SENDT))
        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(Testdata.orgNr)?.navn == "Flex AS"
        }

        sendBehandlingsstatusMelding(behandlingstatusmelding)
        sendBehandlingsstatusMelding(behandlingstatusmelding.copy(status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER))
        awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)
    }

    @Test
    @Order(1)
    fun `Etter 16 dager sender vi forste mangler inntektsmelding varsel`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(16))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = VENTER_PÅ_ARBEIDSGIVER,
            forventetSisteVarselstatus = VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
        )

        forsteVarselBrukervarselId = fangBrukervarselId()
        forsteVarselMeldingId = fangMeldingId()
    }

    @Test
    @Order(2)
    fun `Resending av forste mangler inntektsmelding varsel gir samme UUID-er`() {
        tilbakestillVarslingstilstand(VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE)

        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(16))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = VENTER_PÅ_ARBEIDSGIVER,
            forventetSisteVarselstatus = VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
        )

        fangBrukervarselId() shouldBeEqualTo forsteVarselBrukervarselId
        fangMeldingId() shouldBeEqualTo forsteVarselMeldingId
    }

    @Test
    @Order(3)
    fun `Etter 29 dager sender vi andre mangler inntektsmelding varsel`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(29))
        cronjobResultat[SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = VENTER_PÅ_ARBEIDSGIVER,
            forventetSisteVarselstatus = VARSLET_MANGLER_INNTEKTSMELDING_ANDRE,
        )

        andreVarselBrukervarselId = fangNyttBrukervarselIdBlantToRecords()
        andreVarselMeldingId = fangNyMeldingIdBlantToRecords()

        andreVarselBrukervarselId shouldNotBeEqualTo forsteVarselBrukervarselId
        andreVarselMeldingId shouldNotBeEqualTo forsteVarselMeldingId
    }

    @Test
    @Order(4)
    fun `Resending av andre mangler inntektsmelding varsel gir samme UUID-er`() {
        tilbakestillAndreVarsel()

        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(29))
        cronjobResultat[SENDT_ANDRE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1

        awaitOppdatertStatus(
            forventetSisteSpleisstatus = VENTER_PÅ_ARBEIDSGIVER,
            forventetSisteVarselstatus = VARSLET_MANGLER_INNTEKTSMELDING_ANDRE,
        )

        fangNyttBrukervarselIdBlantToRecords() shouldBeEqualTo andreVarselBrukervarselId
        fangNyMeldingIdBlantToRecords() shouldBeEqualTo andreVarselMeldingId
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

    private fun tilbakestillVarslingstilstand(vararg statuserSomSkalSlettes: StatusVerdi) {
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

    private fun tilbakestillAndreVarsel() {
        val behandling =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                Testdata.vedtaksperiodeId,
                Testdata.behandlingId,
            )!!

        val statusRader =
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(behandling.id!!))

        vedtaksperiodeBehandlingStatusRepository.deleteAll(
            statusRader.filter {
                it.status in listOf(VARSLET_MANGLER_INNTEKTSMELDING_ANDRE, VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE)
            },
        )

        val forsteVarselTidspunkt =
            statusRader
                .first { it.status == VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE }
                .tidspunkt

        vedtaksperiodeBehandlingRepository.save(
            behandling.copy(
                sisteVarslingstatus = VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
                sisteVarslingstatusTidspunkt = forsteVarselTidspunkt,
                oppdatertDatabase = Instant.now(),
            ),
        )
    }
}
