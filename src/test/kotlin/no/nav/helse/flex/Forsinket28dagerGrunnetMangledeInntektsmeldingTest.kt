package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.tms.varsel.action.Sensitivitet
import org.amshove.kluent.*
import org.awaitility.Awaitility
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Forsinket28dagerGrunnetMangledeInntektsmeldingTest : FellesTestOppsett() {
    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
            .shouldBeEmpty()
        sendSoknad(Testdata.soknad)
        sendSoknad(
            Testdata.soknad.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = LocalDateTime.now(),
            ),
        )

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
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
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat.shouldHaveSize(3)
        cronjobResultat[SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15] shouldBeEqualTo 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_28] shouldBeEqualTo 0

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(3)
    fun `Ingenting skjer etter 20 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(20))
        cronjobResultat.shouldHaveSize(2)
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_28] shouldBeEqualTo 0
    }

    @Test
    @Order(4)
    fun `Noe skjer etter 28 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(28))
        cronjobResultat.shouldHaveSize(3)
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_28] shouldBeEqualTo 1

        val status = awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)
        val varselStatusen =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(listOf(status.id!!))
                .first { it.status == VARSLET_MANGLER_INNTEKTSMELDING_28 }

        val varslingRecords = varslingConsumer.ventPåRecords(2)
        val meldingRecords = meldingKafkaConsumer.ventPåRecords(2)

        val doneBrukervarsel = varslingRecords.first()
        doneBrukervarsel.value().tilInaktiverVarselInstance().varselId shouldBeEqualTo doneBrukervarsel.key()
        doneBrukervarsel.value().tilInaktiverVarselInstance().varselId shouldBeEqualTo varselStatusen.brukervarselId

        val cr = meldingRecords.first()
        val doneDittSykefravaer: MeldingKafkaDto = cr.value().let { objectMapper.readValue(it) }

        cr.key() shouldBeEqualTo varselStatusen.dittSykefravaerMeldingId

        doneDittSykefravaer.lukkMelding.shouldNotBeNull()

        val beskjedCR = varslingRecords.last()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo Testdata.fnr
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Saksbehandlingen er forsinket fordi vi mangler inntektsmeldingen fra Flex AS for sykefraværet som startet 29. mai 2022."

        val meldingCR = meldingRecords.last()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo Testdata.fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING_28"
        opprettMelding.tekst shouldBeEqualTo
            "Saksbehandlingen er forsinket fordi vi mangler inntektsmeldingen fra Flex AS for sykefraværet som startet 29. mai 2022."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()
    }
}
