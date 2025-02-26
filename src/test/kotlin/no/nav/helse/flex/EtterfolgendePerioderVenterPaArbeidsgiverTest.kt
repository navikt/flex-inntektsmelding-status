package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.Testdata.soknad
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.varselutsending.CronJobStatus
import no.nav.helse.flex.varselutsending.CronJobStatus.SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING
import no.nav.helse.flex.varselutsending.CronJobStatus.UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

private const val s = "fra Flex AS."

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class EtterfolgendePerioderVenterPaArbeidsgiverTest : FellesTestOppsett() {
    object Arbeidsgiver1 {
        val orgnummer = "123456547"
        val orgnavn = "Flex AS"
        val soknad1 =
            soknad.copy(
                arbeidsgiver =
                    ArbeidsgiverDTO(
                        orgnummer = orgnummer,
                        navn = orgnavn,
                    ),
                id = UUID.randomUUID().toString(),
                startSyketilfelle = LocalDate.of(2022, 5, 28),
                fom = LocalDate.of(2022, 5, 29),
                tom = LocalDate.of(2022, 6, 30),
            )
        val soknad2 =
            soknad1.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 15),
            )
        val vedtaksperiodeId1 = UUID.randomUUID().toString()
        val behandlingId1 = UUID.randomUUID().toString()
        val vedtaksperiodeId2 = UUID.randomUUID().toString()
        val behandlingId2 = UUID.randomUUID().toString()
    }

    object Arbeidsgiver2 {
        val orgnummer = "4567777"
        val orgnavn = "Kebabfabrikken"
        val soknad1 =
            soknad.copy(
                id = UUID.randomUUID().toString(),
                arbeidsgiver =
                    ArbeidsgiverDTO(
                        orgnummer = orgnummer,
                        navn = orgnavn,
                    ),
                startSyketilfelle = LocalDate.of(2022, 5, 28),
                fom = LocalDate.of(2022, 5, 29),
                tom = LocalDate.of(2022, 6, 30),
            )
        val soknad2 =
            soknad1.copy(
                id = UUID.randomUUID().toString(),
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 15),
            )
        val vedtaksperiodeId1 = UUID.randomUUID().toString()
        val behandlingId1 = UUID.randomUUID().toString()
        val vedtaksperiodeId2 = UUID.randomUUID().toString()
        val behandlingId2 = UUID.randomUUID().toString()
    }

    @Test
    @Order(0)
    fun `Vi sender inn 4 søknader, 2 for hver arbeidsgiver og med to perioder etterhverandre`() {
        listOf(Arbeidsgiver1.soknad1, Arbeidsgiver1.soknad2, Arbeidsgiver2.soknad1, Arbeidsgiver2.soknad2).forEach {
            sendSoknad(it)
            sendSoknad(
                it.copy(
                    status = SoknadsstatusDTO.SENDT,
                ),
            )
        }

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.count() == 2L
        }
        await().atMost(5, TimeUnit.SECONDS).until {
            sykepengesoknadRepository.count() == 4L
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at alle perioden venter på arbeidsgiver`() {
        fun sendBehandlingstatusMelding(
            soknad: SykepengesoknadDTO,
            vedtaksperiodeId: String,
            behandlingId: String,
        ) {
            val tidspunkt = OffsetDateTime.now()
            val behandlingstatusmelding =
                Behandlingstatusmelding(
                    vedtaksperiodeId = vedtaksperiodeId,
                    behandlingId = behandlingId,
                    status = Behandlingstatustype.OPPRETTET,
                    tidspunkt = tidspunkt,
                    eksterneSøknadIder = listOf(soknad.id),
                )
            sendBehandlingsstatusMelding(behandlingstatusmelding)
            awaitOppdatertStatus(
                OPPRETTET,
                behandlingId = behandlingId,
                vedtaksperiodeId = vedtaksperiodeId,
            )
            sendBehandlingsstatusMelding(
                behandlingstatusmelding.copy(
                    status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
                ),
            )

            awaitOppdatertStatus(
                VENTER_PÅ_ARBEIDSGIVER,
                behandlingId = behandlingId,
                vedtaksperiodeId = vedtaksperiodeId,
            )
        }
        sendBehandlingstatusMelding(
            soknad = Arbeidsgiver1.soknad1,
            vedtaksperiodeId = Arbeidsgiver1.vedtaksperiodeId1,
            behandlingId = Arbeidsgiver1.behandlingId1,
        )
        sendBehandlingstatusMelding(
            soknad = Arbeidsgiver1.soknad2,
            vedtaksperiodeId = Arbeidsgiver1.vedtaksperiodeId2,
            behandlingId = Arbeidsgiver1.behandlingId2,
        )
        sendBehandlingstatusMelding(
            soknad = Arbeidsgiver2.soknad1,
            vedtaksperiodeId = Arbeidsgiver2.vedtaksperiodeId1,
            behandlingId = Arbeidsgiver2.behandlingId1,
        )
        sendBehandlingstatusMelding(
            soknad = Arbeidsgiver2.soknad2,
            vedtaksperiodeId = Arbeidsgiver2.vedtaksperiodeId2,
            behandlingId = Arbeidsgiver2.behandlingId2,
        )

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first() shouldBeEqualTo fnr

        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
                sendtTidspunkt.minusHours(3).toInstant(),
            ).shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `Vi kan hente ut historikken fra flex internal frontend`() {
        val response = hentVedtaksperioder()
        response shouldHaveSize 4
        response[0].soknader.first().orgnummer shouldBeEqualTo orgNr
        response[0].statuser shouldHaveSize 2
        response[0].vedtaksperiode.sisteSpleisstatus shouldBeEqualTo VENTER_PÅ_ARBEIDSGIVER

        verifiserAuditlogging()
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut mangler inntektsmelding varsel etter 14 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(14))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(16))
        cronjobResultat[SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1

        val brukerVarslinger = varslingConsumer.ventPåRecords(2)
        val beskjedCR = brukerVarslinger.first()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Status i saken din om sykepenger: Vi venter på inntektsmelding fra Flex AS."

        val beskjedCR2 = brukerVarslinger.last().value().tilOpprettVarselInstance()
        beskjedCR2.eksternVarsling.shouldBeNull()
        beskjedCR2.tekster.first().tekst shouldBeEqualTo "Status i saken din om sykepenger: " +
            "Vi venter på inntektsmelding fra Kebabfabrikken."

        val meldinger = meldingKafkaConsumer.ventPåRecords(2)
        val meldingCR = meldinger.first()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo
            "Status i saken din om sykepenger: " +
            "Vi venter på inntektsmelding fra Flex AS."

        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()

        val opprettMeldingTo = objectMapper.readValue<MeldingKafkaDto>(meldinger.last().value())
        opprettMeldingTo.opprettMelding!!.tekst shouldBeEqualTo
            "Status i saken din om sykepenger: " +
            "Vi venter på inntektsmelding fra Kebabfabrikken."
    }

    @Test
    @Order(10)
    fun `Cronjob resultat til slutt`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(18))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 1
        cronjobResultat[CronJobStatus.INGEN_PERIODE_FUNNET_FOR_FØRSTE_MANGLER_INNTEKTSMELDING_VARSEL] shouldBeEqualTo 1
        cronjobResultat[CronJobStatus.UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[CronJobStatus.UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 0
    }
}
