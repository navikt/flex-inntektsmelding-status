package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.FullVedtaksperiodeBehandling
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.tms.varsel.action.Sensitivitet
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest : FellesTestOppsett() {
    private final val fnr = "12345678901"
    private final val vedtaksperiodeId = UUID.randomUUID().toString()
    private final val behandlingId = UUID.randomUUID().toString()
    private final val soknadId = UUID.randomUUID().toString()
    private final val orgNr = "123456547"
    private final val fom = LocalDate.of(2022, 5, 29)
    private final val tom = LocalDate.of(2022, 6, 30)
    val soknad =
        SykepengesoknadDTO(
            fnr = fnr,
            id = soknadId,
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = SoknadsstatusDTO.NY,
            startSyketilfelle = fom,
            fom = fom,
            tom = tom,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr),
        )

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()

        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad
                    .copy(
                        status = SoknadsstatusDTO.SENDT,
                        sendtNav = LocalDateTime.now(),
                    )
                    .serialisertTilString(),
            ),
        ).get()
        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad.serialisertTilString(),
            ),
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at perioden venter på arbeidsgiver`() {
        periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()

        val tidspunkt = OffsetDateTime.now()
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            )
        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()
        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.copy(
                    status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
                ).serialisertTilString(),
            ),
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == VENTER_PÅ_ARBEIDSGIVER
            }
        }
        val perioderSomVenterPaaArbeidsgiver =
            periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first().fnr shouldBeEqualTo fnr
        perioderSomVenterPaaArbeidsgiver.first().sykepengesoknadUuid shouldBeEqualTo soknadId

        periodeStatusRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
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
        cronjobResultat["antallUnikeFnrInntektsmeldingVarsling"] shouldBeEqualTo 0
        cronjobResultat.containsKey("VARSLET_MANGLER_INNTEKTSMELDING").`should be false`()
    }

    @Test
    @Order(4)
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat["VARSLET_MANGLER_INNTEKTSMELDING"] shouldBeEqualTo 1
        cronjobResultat["antallUnikeFnrInntektsmeldingVarsling"] shouldBeEqualTo 1

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

        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == VENTER_PÅ_SAKSBEHANDLER
            }
        }

        val vedtaksperiode =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId,
                behandlingId,
            )
        val statusManglerIm =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(
                listOf(vedtaksperiode!!.id!!),
            ).first { it.status == VARSLET_MANGLER_INNTEKTSMELDING }

        val doneBrukernotifikasjon =
            varslingConsumer
                .ventPåRecords(1)
                .first()
        doneBrukernotifikasjon.key() shouldBeEqualTo statusManglerIm.brukervarselId!!
        doneBrukernotifikasjon.value().tilInaktiverVarselInstance().varselId shouldBeEqualTo statusManglerIm.brukervarselId!!

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
        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == FERDIG
            }
        }
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
        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad
                    .copy(
                        status = SoknadsstatusDTO.SENDT,
                        sendtNav = LocalDateTime.now(),
                        id = korrigerendeSoknadId,
                        korrigerer = soknadId,
                    )
                    .serialisertTilString(),
            ),
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
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

        kafkaProducer.send(
            ProducerRecord(
                SIS_TOPIC,
                vedtaksperiodeId,
                behandlingstatusmelding.serialisertTilString(),
            ),
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            val vedtaksperiode =
                vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                    vedtaksperiodeId,
                    behandlingId,
                )
            if (vedtaksperiode == null) {
                false
            } else {
                vedtaksperiode.sisteSpleisstatus == StatusVerdi.VENTER_PÅ_SAKSBEHANDLER
            }
        }
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

    @Test
    @Order(99)
    fun `Trenger riktig auth for å hente data med api`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/vedtaksperioder")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/vedtaksperioder")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }
}
