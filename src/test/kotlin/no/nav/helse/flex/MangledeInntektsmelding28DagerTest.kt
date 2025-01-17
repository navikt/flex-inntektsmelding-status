package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
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
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MangledeInntektsmelding28DagerTest : FellesTestOppsett() {
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
    fun `Vi venter på arbeidsgiver`() {
        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = Testdata.vedtaksperiodeId,
                behandlingId = Testdata.behandlingId,
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(Testdata.soknadId),
            )
        sendBehandlingsstatusMelding(behandlingstatusmelding)
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
            ),
        )

        awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler inntektsmelding varsel etter 21 dager (egentlig 15 men tester tid mellom)`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(21))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(3)
    fun `Ingenting skjer etter 22 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(22))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
    }

    @Test
    @Order(4)
    fun `Det må gå nok tid før neste varsel`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(28).plusHours(1))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1
        cronjobResultat[HAR_FATT_NYLIG_VARSEL] shouldBeEqualTo 1
    }

    @Test
    @Order(5)
    fun `Noe skjer etter 32 dager hvis det har gått nok tid siden sist varsel`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(32))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1
        cronjobResultat[HAR_FATT_NYLIG_VARSEL].shouldBeNull()

        val status = awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)
        val denNyeVarselstatusen =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .first { it.status == VARSLET_MANGLER_INNTEKTSMELDING_ANDRE }

        val denForrigeVarselstatusen =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .first { it.status == VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE }

        val varslingRecords = varslingConsumer.ventPåRecords(2)
        val meldingRecords = meldingKafkaConsumer.ventPåRecords(2)

        val doneBrukervarsel = varslingRecords.first()
        doneBrukervarsel.value()
            .tilInaktiverVarselInstance().varselId shouldBeEqualTo denForrigeVarselstatusen.brukervarselId

        val doneMeldingDittSykefravar = meldingRecords.first()
        val doneDittSykefravaer: MeldingKafkaDto =
            doneMeldingDittSykefravar.value().let { objectMapper.readValue(it) }

        doneMeldingDittSykefravar.key() shouldBeEqualTo denForrigeVarselstatusen.dittSykefravaerMeldingId
        doneDittSykefravaer.lukkMelding.shouldNotBeNull()

        val beskjedOpprettVarsel = varslingRecords.last()
        val beskjedInput = beskjedOpprettVarsel.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo Testdata.fnr
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.varselId shouldBeEqualTo denNyeVarselstatusen.brukervarselId
        beskjedInput.link shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Status for sykefraværet som startet 29. mai 2022:\n" +
            "Saksbehandlingen er forsinket fordi vi fortsatt venter på inntektsmelding fra Flex AS."

        val opprettMeldingCr = meldingRecords.last()
        val melding = objectMapper.readValue<MeldingKafkaDto>(opprettMeldingCr.value())
        melding.fnr shouldBeEqualTo Testdata.fnr
        melding.lukkMelding.shouldBeNull()
        opprettMeldingCr.key() shouldBeEqualTo denNyeVarselstatusen.dittSykefravaerMeldingId

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING_28"
        @Suppress("ktlint:standard:max-line-length")
        opprettMelding.tekst shouldBeEqualTo
            "Status for sykefraværet som startet 29. mai 2022:\n" +
            "Saksbehandlingen er forsinket fordi vi fortsatt venter på inntektsmelding fra Flex AS."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()

        // Forskjellige IDer
        doneMeldingDittSykefravar.key() shouldNotBeEqualTo opprettMeldingCr.key()
        beskjedOpprettVarsel.key() shouldNotBeEqualTo doneBrukervarsel.key()
    }
}
