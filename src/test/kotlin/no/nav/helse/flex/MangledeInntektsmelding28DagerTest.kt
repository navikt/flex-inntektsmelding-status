package no.nav.helse.flex

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.varselutsending.CronJobStatus.HAR_FATT_NYLIG_VARSEL
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING
import no.nav.helse.flex.varselutsending.CronJobStatus.UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING
import no.nav.helse.flex.varselutsending.CronJobStatus.UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.SpleisStatus
import no.nav.helse.flex.vedtaksperiodebehandling.VarslingStatus.VARSLET_MANGLER_INNTEKTSMELDING_ANDRE
import no.nav.helse.flex.vedtaksperiodebehandling.VarslingStatus.VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE
import no.nav.tms.varsel.action.Sensitivitet
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MangledeInntektsmelding28DagerTest : FellesTestOppsett() {
    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.toInstant())
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

        awaitOppdatertStatus(SpleisStatus.VENTER_PÅ_ARBEIDSGIVER)
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler inntektsmelding varsel etter 21 dager (egentlig 15 men tester tid mellom)`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(21))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBe 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBe 0

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(3)
    fun `Ingenting skjer etter 22 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(22))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBe 0
    }

    @Test
    @Order(4)
    fun `Det må gå nok tid før neste varsel`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(28).plusHours(1))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBe 1
        cronjobResultat[HAR_FATT_NYLIG_VARSEL] shouldBe 1
    }

    @Test
    @Order(5)
    fun `Noe skjer etter 32 dager hvis det har gått nok tid siden sist varsel`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(32))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBe 1
        cronjobResultat[HAR_FATT_NYLIG_VARSEL].shouldBeNull()

        val status = awaitOppdatertStatus(SpleisStatus.VENTER_PÅ_ARBEIDSGIVER)
        val denNyeVarselstatusen =
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .first { it.status == VARSLET_MANGLER_INNTEKTSMELDING_ANDRE }

        val denForrigeVarselstatusen =
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(listOf(status.id))
                .first { it.status == VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE }

        val varslingRecords = varslingConsumer.ventPåRecords(2)
        val meldingRecords = meldingKafkaConsumer.ventPåRecords(2)

        val doneBrukervarsel = varslingRecords.first()
        doneBrukervarsel
            .value()
            .tilInaktiverVarselInstance()
            .varselId shouldBe denForrigeVarselstatusen.brukervarselId

        val doneMeldingDittSykefravar = meldingRecords.first()
        val doneDittSykefravaer: MeldingKafkaDto =
            doneMeldingDittSykefravar.value().let { objectMapper.readValue(it) }

        doneMeldingDittSykefravar.key() shouldBe denForrigeVarselstatusen.dittSykefravaerMeldingId
        doneDittSykefravaer.lukkMelding.shouldNotBeNull()

        val beskjedOpprettVarsel = varslingRecords.last()
        val beskjedInput = beskjedOpprettVarsel.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBe Testdata.fnr
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.varselId shouldBe denNyeVarselstatusen.brukervarselId
        beskjedInput.link shouldBe "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBe Sensitivitet.High
        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBe
            "Status i saken din om sykepenger: " +
            "Vi mangler fortsatt inntektsmelding fra Flex AS og har sendt en påminnelse til arbeidsgiveren din om dette." +
            "Når vi får den kan vi begynne å behandle søknaden din."
        val opprettMeldingCr = meldingRecords.last()
        val melding = objectMapper.readValue<MeldingKafkaDto>(opprettMeldingCr.value())
        melding.fnr shouldBe Testdata.fnr
        melding.lukkMelding.shouldBeNull()
        opprettMeldingCr.key() shouldBe denNyeVarselstatusen.dittSykefravaerMeldingId

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBe "MANGLENDE_INNTEKTSMELDING_28"
        @Suppress("ktlint:standard:max-line-length")
        opprettMelding.tekst shouldBe
            "Status i saken din om sykepenger: " +
            "Vi mangler fortsatt inntektsmelding fra Flex AS og har sendt en påminnelse til arbeidsgiveren din om dette." +
            "Når vi får den kan vi begynne å behandle søknaden din."

        opprettMelding.lenke shouldBe "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBe false
        opprettMelding.variant shouldBe Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()

        // Forskjellige IDer
        doneMeldingDittSykefravar.key() shouldNotBe opprettMeldingCr.key()
        beskjedOpprettVarsel.key() shouldNotBe doneBrukervarsel.key()
    }
}
