package no.nav.helse.flex

import no.nav.helse.flex.Testdata.behandlingId
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.soknad
import no.nav.helse.flex.Testdata.vedtaksperiodeId
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class EtterfolgendePerioderVenterPaSaksbehandlerTest : FellesTestOppsett() {
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
                    sendtNav = LocalDateTime.now(),
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
    fun `Vi får beskjed at alle periodene venter på saksbehandler`() {
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
            awaitOppdatertStatus(
                VENTER_PÅ_SAKSBEHANDLER,
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
            vedtaksperiodeBehandlingRepository.finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first() shouldBeEqualTo fnr

        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
            OffsetDateTime.now().minusHours(3).toInstant(),
        ).shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `Vi lagrer to inntektsmeldinger fra HAG`() {
        sendInntektsmelding(
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = Arbeidsgiver1.orgnummer,
                refusjonBelopPerMnd = BigDecimal(5000),
                beregnetInntekt = BigDecimal(10000),
                vedtaksperiodeId = Arbeidsgiver1.vedtaksperiodeId1,
            ),
        )
        sendInntektsmelding(
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = Arbeidsgiver2.orgnummer,
                refusjonBelopPerMnd = BigDecimal(5000),
                beregnetInntekt = BigDecimal(10000),
                vedtaksperiodeId = Arbeidsgiver2.vedtaksperiodeId1,
            ),
        )
        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).count() == 2
        }
    }

    @Test
    @Order(2)
    fun `Vi kan hente ut historikken fra flex internal frontend`() {
        val response = hentVedtaksperioder()
        response shouldHaveSize 4
        response[0].soknader.first().orgnummer shouldBeEqualTo orgNr
        response[0].statuser shouldHaveSize 3
        response[0].vedtaksperiode.sisteSpleisstatus shouldBeEqualTo VENTER_PÅ_SAKSBEHANDLER
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut mangler inntektsmelding varsel etter 16 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat.containsKey(SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15).`should be false`()
    }

    @Test
    @Order(4)
    fun `Vi sender ut forsinket saksbehandling varsel etter 30 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(30))
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 1
        cronjobResultat.containsKey(SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15).`should be false`()
        cronjobResultat[SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 1

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
        await().pollDelay(1, TimeUnit.SECONDS).until { true }
    }

    @Test
    @Order(5)
    fun `Vi sender ikke ut forsinket saksbehandling varsel etter 31 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(31))
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 1
        cronjobResultat.containsKey(SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15).`should be false`()
        cronjobResultat[SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28].shouldBeNull()
        cronjobResultat[INGEN_PERIODE_FUNNET_FOR_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        varslingConsumer.ventPåRecords(0)
        meldingKafkaConsumer.ventPåRecords(0)
    }

    @Test
    @Order(10)
    fun `Cronjob resultat til slutt`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(50))
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_28] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 1
        cronjobResultat.shouldHaveSize(4)
    }
}
