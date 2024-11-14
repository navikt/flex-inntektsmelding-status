package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.varseltekst.SAKSBEHANDLINGSTID_URL
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.tms.varsel.action.Sensitivitet
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VenterPaSaksbehandlerMedRevarslingTest : FellesTestOppsett() {
    val tidspunkt = sendtTidspunkt
    val behandlingstatusmelding =
        Behandlingstatusmelding(
            vedtaksperiodeId = Testdata.vedtaksperiodeId,
            behandlingId = Testdata.behandlingId,
            status = Behandlingstatustype.OPPRETTET,
            tidspunkt = tidspunkt,
            eksterneSøknadIder = listOf(Testdata.soknadId),
        )

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.toInstant())
            .shouldBeEmpty()
        sendSoknad(Testdata.soknad)
        sendSoknad(
            Testdata.soknad.copy(
                status = SoknadsstatusDTO.SENDT,
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(Testdata.orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi venter på saksbehandler`() {
        sendBehandlingsstatusMelding(behandlingstatusmelding)
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
            ),
        )
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER,
            ),
        )

        awaitOppdatertStatus(VENTER_PÅ_SAKSBEHANDLER)
    }

    @Test
    @Order(2)
    fun `Ingenting skjer etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(16))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING].shouldBeNull()
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0

        varslingConsumer.ventPåRecords(0)
        meldingKafkaConsumer.ventPåRecords(0)
    }

    @Test
    @Order(2)
    fun `Vi lagrer en inntektsmelding fra HAG`() {
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
    @Order(3)
    fun `Ingenting skjer etter 20 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(20))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
    }

    @Test
    @Order(4)
    fun `Etter 28 dager sender vi varsel om forsinket saksbehandling`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(28).plusMinutes(1))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        val status =
            awaitOppdatertStatus(
                forventetSisteSpleisstatus = VENTER_PÅ_SAKSBEHANDLER,
                forventetSisteVarselstatus = VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
            )
        val varselStatusen =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .first { it.status == VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE }
        val beskjedCR = varslingConsumer.ventPåRecords(1).first()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.varselId shouldBeEqualTo varselStatusen.brukervarselId
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo SAKSBEHANDLINGSTID_URL
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Behandlingen av søknaden din om sykepenger tar lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører. Se vår oversikt over forventet saksbehandlingstid."

        val meldingCR = meldingKafkaConsumer.ventPåRecords(1).first()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "FORSINKET_SAKSBEHANDLING_FORSTE_VARSEL"
        @Suppress("ktlint:standard:max-line-length")
        opprettMelding.tekst shouldBeEqualTo
            "Behandlingen av søknaden din om sykepenger tar lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører. Se vår oversikt over forventet saksbehandlingstid."
        opprettMelding.lenke shouldBeEqualTo SAKSBEHANDLINGSTID_URL
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()
    }

    @Test
    @Order(5)
    fun `Etter 60 dager sender vi revarsel om forsinket saksbehandling`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(60))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING].shouldBeNull()
        cronjobResultat[SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        val status =
            awaitOppdatertStatus(
                forventetSisteSpleisstatus = VENTER_PÅ_SAKSBEHANDLER,
                forventetSisteVarselstatus = REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
            )

        val varslingRecords = varslingConsumer.ventPåRecords(2)

        val varselStatusen =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .sortedBy { it.tidspunkt }
                .first { it.status == REVARSLET_VENTER_PÅ_SAKSBEHANDLER }

        val beskjedCR = varslingRecords.last()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.varselId shouldBeEqualTo varselStatusen.brukervarselId
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo SAKSBEHANDLINGSTID_URL
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Beklager, men behandlingen av søknaden din om sykepenger tar enda lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører."

        val meldingRecords = meldingKafkaConsumer.ventPåRecords(2)

        val meldingCR = meldingRecords.last()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "FORSINKET_SAKSBEHANDLING_REVARSEL"
        @Suppress("ktlint:standard:max-line-length")
        opprettMelding.tekst shouldBeEqualTo
            "Beklager, men behandlingen av søknaden din om sykepenger tar enda lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører."
        opprettMelding.lenke shouldBeEqualTo SAKSBEHANDLINGSTID_URL
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()
    }

    @Test
    @Order(6)
    fun `Etter 70 dager skjer ingenting`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(70))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_REVARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 0
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING].shouldBeNull()
        cronjobResultat[SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING].shouldBeNull()
    }

    @Test
    @Order(7)
    fun `Etter 95 dager sender vi igjen revarsel om forsinket saksbehandling`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(95).plusMinutes(2))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_REVARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING].shouldBeNull()
        cronjobResultat[SENDT_REVARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        val status =
            awaitOppdatertStatus(
                forventetSisteSpleisstatus = VENTER_PÅ_SAKSBEHANDLER,
                forventetSisteVarselstatus = REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
            )

        val varslingRecords = varslingConsumer.ventPåRecords(2)

        val varselStatusen =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .sortedBy { it.tidspunkt }
                .last { it.status == REVARSLET_VENTER_PÅ_SAKSBEHANDLER }

        val beskjedCR = varslingRecords.last()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.varselId shouldBeEqualTo varselStatusen.brukervarselId
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo SAKSBEHANDLINGSTID_URL
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Beklager, men behandlingen av søknaden din om sykepenger tar enda lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører."

        val meldingRecords = meldingKafkaConsumer.ventPåRecords(2)

        val meldingCR = meldingRecords.last()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "FORSINKET_SAKSBEHANDLING_REVARSEL"
        @Suppress("ktlint:standard:max-line-length")
        opprettMelding.tekst shouldBeEqualTo
            "Beklager, men behandlingen av søknaden din om sykepenger tar enda lengre tid enn forventet. Vi beklager eventuelle ulemper dette medfører."
        opprettMelding.lenke shouldBeEqualTo SAKSBEHANDLINGSTID_URL
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()
    }

    @Test
    @Order(9)
    fun `Alle brukervarselIDer skal være unike`() {
        val status =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                Testdata.vedtaksperiodeId,
                Testdata.behandlingId,
            )!!
        val brukerVarselIder =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .sortedBy { it.opprettetDatabase }
                .filter { !it.status.toString().contains("DONE") }
                .mapNotNull { it.brukervarselId }

        // Alle brukervarselIDer skal være unike
        brukerVarselIder.size shouldBeEqualTo brukerVarselIder.toSet().size
    }

    @Test
    @Order(10)
    fun `Saken blir ferdigbehandlet eller kastet ut og vi donner meldingene`() {
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = listOf(Behandlingstatustype.FERDIG, Behandlingstatustype.BEHANDLES_UTENFOR_SPEIL).random(),
            ),
        )

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }
}
