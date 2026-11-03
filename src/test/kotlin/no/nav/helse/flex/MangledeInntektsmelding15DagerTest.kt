package no.nav.helse.flex

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.Testdata.behandlingId
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.Testdata.soknad
import no.nav.helse.flex.Testdata.soknadId
import no.nav.helse.flex.Testdata.vedtaksperiodeId
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.varselutsending.CronJobStatus
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING
import no.nav.helse.flex.varselutsending.CronJobStatus.UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.SpleisStatus
import no.nav.helse.flex.vedtaksperiodebehandling.VarslingStatus.*
import no.nav.tms.varsel.action.Sensitivitet
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MangledeInntektsmelding15DagerTest : FellesTestOppsett() {
    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.toInstant())
            .shouldBeEmpty()
        sendSoknad(soknad)
        sendSoknad(
            soknad.copy(
                status = SoknadsstatusDTO.SENDT,
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at perioden venter på arbeidsgiver`() {
        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.plusMinutes(1).toInstant())
            .shouldBeEmpty()

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

        awaitOppdatertStatus(SpleisStatus.VENTER_PÅ_ARBEIDSGIVER)

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.plusMinutes(1).toInstant())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first() shouldBe fnr

        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
                sendtTidspunkt.toInstant(),
            ).shouldBeEmpty()
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut mangler inntektsmelding varsel etter 14 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(14))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 0
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).shouldBeFalse()
    }

    @Test
    @Order(4)
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(16))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBe 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 1

        val beskjedCR = varslingConsumer.ventPåRecords(1).first()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBe fnr
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBe "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBe Sensitivitet.High
        beskjedInput.tekster.first().tekst shouldBe
            "Status i saken din om sykepenger: " +
            "Vi venter på inntektsmelding fra Flex AS."

        val meldingCR = meldingKafkaConsumer.ventPåRecords(1).first()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBe fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBe "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBe
            "Status i saken din om sykepenger: " +
            "Vi venter på inntektsmelding fra Flex AS."
        opprettMelding.lenke shouldBe "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBe false
        opprettMelding.variant shouldBe Variant.INFO
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

        val vedtaksperiode = awaitOppdatertStatus(SpleisStatus.VENTER_PÅ_SAKSBEHANDLER)

        val statusManglerIm =
            vedtaksperiodeBehandlingStatusRepository
                .findByVedtaksperiodeBehandlingIdIn(
                    listOf(vedtaksperiode.id!!),
                ).first { it.status == VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE }

        val doneBrukervarsel =
            varslingConsumer
                .ventPåRecords(1)
                .first()
        doneBrukervarsel.key() shouldBe statusManglerIm.brukervarselId!!
        doneBrukervarsel.value().tilInaktiverVarselInstance().varselId shouldBe statusManglerIm.brukervarselId

        val cr = meldingKafkaConsumer.ventPåRecords(1).first()
        val doneDittSykefravaer: MeldingKafkaDto = cr.value().let { objectMapper.readValue(it) }

        cr.key() shouldBe statusManglerIm.dittSykefravaerMeldingId!!
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

        awaitOppdatertStatus(SpleisStatus.FERDIG)
    }

    @Test
    @Order(7)
    fun `Vi kan hente ut historikken fra flex internal frontend igjen`() {
        val response = hentVedtaksperioder()
        response shouldHaveSize 1
        response
            .first()
            .soknader
            .first()
            .orgnummer shouldBe orgNr
        response.first().statuser shouldHaveSize 2

        response.first().statuser.map { it.status.name } shouldBe
            listOf(
                "VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE",
                "VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE",
            )

        response.first().vedtaksperiode.sisteSpleisstatus shouldBe SpleisStatus.FERDIG
        response.first().vedtaksperiode.sisteVarslingstatus shouldBe VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE

        verifiserAuditlogging()
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

        awaitOppdatertStatus(SpleisStatus.VENTER_PÅ_SAKSBEHANDLER)
    }

    @Test
    @Order(9)
    fun `Vi kan enda en gang hente ut historikken fra flex internal frontend`() {
        val response = hentVedtaksperioder()
        response shouldHaveSize 1
        response.first().soknader.shouldHaveSize(2)
        response
            .first()
            .soknader
            .first()
            .orgnummer shouldBe orgNr
        response.first().statuser shouldHaveSize 2

        response.first().statuser.map { it.status.name } shouldBe
            listOf(
                "VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE",
                "VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE",
            )

        response.first().vedtaksperiode.sisteSpleisstatus shouldBe SpleisStatus.VENTER_PÅ_SAKSBEHANDLER
        response.first().vedtaksperiode.sisteVarslingstatus shouldBe VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE

        verifiserAuditlogging()
    }

    @Test
    @Order(10)
    fun `Cronjob resultat til slutt`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(14))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBe 0
        cronjobResultat[CronJobStatus.UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBe 0
        cronjobResultat[CronJobStatus.UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBe 0
    }
}
