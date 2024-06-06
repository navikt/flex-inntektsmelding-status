package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.behandlingId
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.soknad
import no.nav.helse.flex.Testdata.soknadId
import no.nav.helse.flex.Testdata.vedtaksperiodeId
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15
import no.nav.helse.flex.varselutsending.CronJobStatus.UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.tms.varsel.action.Sensitivitet
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest : FellesTestOppsett() {
    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()
        sendSoknad(soknad)
        sendSoknad(
            soknad.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = LocalDateTime.now(),
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at perioden venter på arbeidsgiver`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()

        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            )
        sendBehandlingsstatusMelding(behandlingstatusmelding)
        sendBehandlingsstatusMelding(
            behandlingstatusmelding.copy(
                status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
            ),
        )

        awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first() shouldBeEqualTo fnr

        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
            OffsetDateTime.now().minusHours(3).toInstant(),
        )
            .shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `Vi kan hente ut historikken fra flex internal frontend`() {
        val responseString =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/vedtaksperioder")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .accept("application/json; charset=UTF-8")
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful).andReturn().response.contentAsString

        val response: List<FullVedtaksperiodeBehandling> = objectMapper.readValue(responseString)
        response shouldHaveSize 1
        response[0].soknader.first().orgnummer shouldBeEqualTo orgNr
        response[0].statuser shouldHaveSize 2
        response[0].vedtaksperiode.sisteSpleisstatus shouldBeEqualTo VENTER_PÅ_ARBEIDSGIVER
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut mangler inntektsmelding varsel etter 14 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(14))
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat.containsKey(SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15).`should be false`()
    }

    @Test
    @Order(4)
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat[SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15] shouldBeEqualTo 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 1

        val beskjedCR = varslingConsumer.ventPåRecords(1).first()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Du har gjort din del. Nå venter vi på inntektsmeldingen fra Flex AS for sykefraværet som startet 29. mai 2022."

        val meldingCR = meldingKafkaConsumer.ventPåRecords(1).first()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo
            "Du har gjort din del. Nå venter vi på inntektsmeldingen fra Flex AS for sykefraværet som startet 29. mai 2022."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()
    }

    @Test
    @Order(5)
    fun `Vi får beskjed at perioden venter på saksbehandling`() {
        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            )

        sendBehandlingsstatusMelding(behandlingstatusmelding)

        val vedtaksperiode = awaitOppdatertStatus(VENTER_PÅ_SAKSBEHANDLER)

        val statusManglerIm =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(
                listOf(vedtaksperiode.id!!),
            ).first { it.status == VARSLET_MANGLER_INNTEKTSMELDING }

        val doneBrukervarsel =
            varslingConsumer
                .ventPåRecords(1)
                .first()
        doneBrukervarsel.key() shouldBeEqualTo statusManglerIm.brukervarselId!!
        doneBrukervarsel.value().tilInaktiverVarselInstance().varselId shouldBeEqualTo statusManglerIm.brukervarselId!!

        val cr = meldingKafkaConsumer.ventPåRecords(1).first()
        val doneDittSykefravaer: MeldingKafkaDto = cr.value().let { objectMapper.readValue(it) }

        cr.key() shouldBeEqualTo statusManglerIm.dittSykefravaerMeldingId!!
        doneDittSykefravaer.lukkMelding.shouldNotBeNull()
    }

    @Test
    @Order(6)
    fun `Vi får beskjed at perioden er ferdig`() {
        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.FERDIG,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            )
        sendBehandlingsstatusMelding(behandlingstatusmelding)

        awaitOppdatertStatus(FERDIG)
    }

    @Test
    @Order(7)
    fun `Vi kan hente ut historikken fra flex internal frontend igjen`() {
        val responseString =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/vedtaksperioder")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .accept("application/json; charset=UTF-8")
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful).andReturn().response.contentAsString

        val response: List<FullVedtaksperiodeBehandling> = objectMapper.readValue(responseString)
        response shouldHaveSize 1
        response.first().soknader.first().orgnummer shouldBeEqualTo orgNr
        response.first().statuser shouldHaveSize 6

        response.first().statuser.map { it.status.name } shouldBeEqualTo
            listOf(
                "OPPRETTET",
                "VENTER_PÅ_ARBEIDSGIVER",
                "VARSLET_MANGLER_INNTEKTSMELDING",
                "VENTER_PÅ_SAKSBEHANDLER",
                "VARSLET_MANGLER_INNTEKTSMELDING_DONE",
                "FERDIG",
            )

        response.first().vedtaksperiode.sisteSpleisstatus shouldBeEqualTo FERDIG
        response.first().vedtaksperiode.sisteVarslingstatus shouldBeEqualTo VARSLET_MANGLER_INNTEKTSMELDING_DONE
    }

    @Test
    @Order(8)
    fun `Vi får beskjed at perioden venter på saksbehandling igjen med enda en ny søknad id`() {
        val korrigerendeSoknadId = UUID.randomUUID().toString()

        sendSoknad(
            soknad
                .copy(
                    status = SoknadsstatusDTO.SENDT,
                    sendtNav = LocalDateTime.now(),
                    id = korrigerendeSoknadId,
                    korrigerer = soknadId,
                ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            sykepengesoknadRepository.findBySykepengesoknadUuid(korrigerendeSoknadId) != null
        }

        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId, korrigerendeSoknadId),
            )

        sendBehandlingsstatusMelding(behandlingstatusmelding)

        awaitOppdatertStatus(VENTER_PÅ_SAKSBEHANDLER)
    }

    @Test
    @Order(9)
    fun `Vi kan enda en gang hente ut historikken fra flex internal frontend`() {
        val responseString =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/vedtaksperioder")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .accept("application/json; charset=UTF-8")
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful).andReturn().response.contentAsString

        val response: List<FullVedtaksperiodeBehandling> = objectMapper.readValue(responseString)
        response shouldHaveSize 1
        response.first().soknader.shouldHaveSize(2)
        response.first().soknader.first().orgnummer shouldBeEqualTo orgNr
        response.first().statuser shouldHaveSize 7

        response.first().statuser.map { it.status.name } shouldBeEqualTo
            listOf(
                "OPPRETTET",
                "VENTER_PÅ_ARBEIDSGIVER",
                "VARSLET_MANGLER_INNTEKTSMELDING",
                "VENTER_PÅ_SAKSBEHANDLER",
                "VARSLET_MANGLER_INNTEKTSMELDING_DONE",
                "FERDIG",
                "VENTER_PÅ_SAKSBEHANDLER",
            )

        response.first().vedtaksperiode.sisteSpleisstatus shouldBeEqualTo VENTER_PÅ_SAKSBEHANDLER
        response.first().vedtaksperiode.sisteVarslingstatus shouldBeEqualTo VARSLET_MANGLER_INNTEKTSMELDING_DONE
    }
}
