package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
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
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LikesteInntektsmeldingMatchTest : FellesTestOppsett() {
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
        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
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
                virksomhetsnummer = "123456547",
                refusjonBelopPerMnd = BigDecimal(5000),
                beregnetInntekt = BigDecimal(10000),
                foersteFravaersdag = LocalDate.of(2022, 5, 28),
                vedtaksperiodeId = UUID.randomUUID().toString(),
            ),
        )
        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
    }

    @Test
    @Order(3)
    fun `Ingenting skjer etter 48 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(48))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
    }

    @Test
    @Order(4)
    fun `Etter 56 dager sender vi varsel om forsinket saksbehandling`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(56).plusMinutes(1))
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
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .first { it.status == VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE }
        val beskjedCR = varslingConsumer.ventPåRecords(1).first()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.varselId shouldBeEqualTo varselStatusen.brukervarselId
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link.shouldBeNull()
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Status i saken din om sykepenger: Vi beklager at saksbehandlingen tar lenger tid enn forventet. Vi behandler søknaden din så raskt vi kan. Trenger du mer informasjon om saken din, kan du skrive til oss eller ringe 55 55 33 33."

        val meldingCR = meldingKafkaConsumer.ventPåRecords(1).first()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "FORSINKET_SAKSBEHANDLING_FORSTE_VARSEL"
        @Suppress("ktlint:standard:max-line-length")
        opprettMelding.tekst shouldBeEqualTo
            "Status i saken din om sykepenger: Vi beklager at saksbehandlingen tar lenger tid enn forventet. Vi behandler søknaden din så raskt vi kan. Trenger du mer informasjon om saken din, kan du skrive til oss eller ringe 55 55 33 33."
        opprettMelding.lenke.shouldBeEqualTo("https://www.nav.no/saksbehandlingstider#sykepenger")
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()
    }

    @Test
    @Order(5)
    fun `Saken blir behandlet og vi donner meldingene`() {
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.FERDIG,
            ),
        )

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }
}
