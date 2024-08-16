package no.nav.helse.flex

import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.soknad
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
class DelayMellomVenterPaSaksbehandlerTest : FellesTestOppsett() {
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

        val vedtaksperiodeId1 = UUID.randomUUID().toString()
        val behandlingId1 = UUID.randomUUID().toString()
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

        val vedtaksperiodeId1 = UUID.randomUUID().toString()
        val behandlingId1 = UUID.randomUUID().toString()
    }

    @Test
    @Order(0)
    fun `Vi sender inn 2 søknader,1 for hver arbeidsgiver med litt forskjellig sendt tider`() {
        sendSoknad(Arbeidsgiver1.soknad1)
        sendSoknad(
            Arbeidsgiver1.soknad1.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = LocalDateTime.now(),
            ),
        )

        sendSoknad(Arbeidsgiver2.soknad1)
        sendSoknad(
            Arbeidsgiver2.soknad1.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = LocalDateTime.now().plusDays(11),
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.count() == 2L
        }
        await().atMost(5, TimeUnit.SECONDS).until {
            sykepengesoknadRepository.count() == 2L
        }
        val soknadene = sykepengesoknadRepository.findAll().toList()
        soknadene.shouldHaveSize(2)
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
            soknad = Arbeidsgiver2.soknad1,
            vedtaksperiodeId = Arbeidsgiver2.vedtaksperiodeId1,
            behandlingId = Arbeidsgiver2.behandlingId1,
        )

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(
                Instant.now(),
            )
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
    @Order(3)
    fun `Vi sender ikke ut mangler inntektsmelding varsel etter 16 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
    }

    @Test
    @Order(4)
    fun `Vi sender ut forsinket saksbehandling varsel etter 30 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(30))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(5)
    fun `Vi sender ikke ut forsinket saksbehandling varsel på annen orgnummer fordi den andre har aktiv varsling`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(40))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat[VARSLER_ALLEREDE_OM_VENTER_PA_SAKSBEHANDLER] shouldBeEqualTo 1
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING].shouldBeNull()
        cronjobResultat[INGEN_PERIODE_FUNNET_FOR_FØRSTE_FORSINKET_SAKSBEHANDLING_VARSEL].shouldBeNull()

        varslingConsumer.ventPåRecords(0)
        meldingKafkaConsumer.ventPåRecords(0)
    }

    // Lukk den ene perioden

    @Test
    @Order(6)
    fun `Den ene perioden blir ferdig`() {
        sendBehandlingsstatusMelding(
            Behandlingstatusmelding(
                vedtaksperiodeId = Arbeidsgiver1.vedtaksperiodeId1,
                behandlingId = Arbeidsgiver1.behandlingId1,
                status = Behandlingstatustype.FERDIG,
                tidspunkt = OffsetDateTime.now(),
                eksterneSøknadIder = emptyList(),
            ),
        )
        awaitOppdatertStatus(
            FERDIG,
            vedtaksperiodeId = Arbeidsgiver1.vedtaksperiodeId1,
            behandlingId = Arbeidsgiver1.behandlingId1,
        )
        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(7)
    fun `Vi sender ikke ut forsinket saksbehandling varsel uten at det har gått nok tid`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(40))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat[HAR_FATT_NYLIG_VARSEL] shouldBeEqualTo 1
        cronjobResultat[VARSLER_ALLEREDE_OM_VENTER_PA_SAKSBEHANDLER].shouldBeNull()
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING].shouldBeNull()
        cronjobResultat[INGEN_PERIODE_FUNNET_FOR_FØRSTE_FORSINKET_SAKSBEHANDLING_VARSEL].shouldBeNull()

        varslingConsumer.ventPåRecords(0)
        meldingKafkaConsumer.ventPåRecords(0)
    }

    @Test
    @Order(8)
    fun `Vi sender ut forsinket saksbehandling varsel etter 45 dager når det har gått nok sleep tid`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(45))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat[INGEN_PERIODE_FUNNET_FOR_FØRSTE_FORSINKET_SAKSBEHANDLING_VARSEL].shouldBeNull()
        cronjobResultat[HAR_FATT_NYLIG_VARSEL].shouldBeNull()

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(10)
    fun `Cronjob resultat til slutt`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(50))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
    }
}
