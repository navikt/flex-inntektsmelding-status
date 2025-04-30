package no.nav.helse.flex

import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.sendtTidspunkt
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
import java.time.LocalDate
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
            vedtaksperiodeBehandlingRepository.finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(
                sendtTidspunkt.plusDays(29).toInstant(),
            )
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first() shouldBeEqualTo fnr

        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
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
        verifiserAuditlogging()
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut mangler inntektsmelding varsel etter 16 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(16))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
    }

    @Test
    @Order(4)
    fun `Vi sender ut forsinket saksbehandling varsel etter 58 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(58))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(5)
    fun `Vi sender ikke ut forsinket saksbehandling varsel etter 70 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(70))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
        cronjobResultat.containsKey(SENDT_FØRSTE_VARSEL_MANGLER_INNTEKTSMELDING).`should be false`()
        cronjobResultat[SENDT_FØRSTE_VARSEL_FORSINKET_SAKSBEHANDLING].shouldBeNull()
        cronjobResultat[VARSLER_ALLEREDE_OM_VENTER_PA_SAKSBEHANDLER] shouldBeEqualTo 1

        varslingConsumer.ventPåRecords(0)
        meldingKafkaConsumer.ventPåRecords(0)
    }

    @Test
    @Order(10)
    fun `Cronjob resultat til slutt`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(78))
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_ANDRE_MANGLER_INNTEKTSMELDING] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FØRSTE_FORSINKET_SAKSBEHANDLING] shouldBeEqualTo 1
    }
}
