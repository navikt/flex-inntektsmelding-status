package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Testdata.behandlingId
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.Testdata.soknad
import no.nav.helse.flex.Testdata.soknadId
import no.nav.helse.flex.Testdata.vedtaksperiodeId
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
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
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ManglerInntektsmeldingFraToArbeidsgivereTest : FellesTestOppsett() {
    val orgnr2 = "4567777"
    val orgnavn2 = "Kebabfabrikken"
    val annenSoknad =
        soknad.copy(
            id = UUID.randomUUID().toString(),
            arbeidsgiver = ArbeidsgiverDTO(orgnummer = orgnr2, navn = orgnavn2),
        )
    val annenVedtaksperiodeId = UUID.randomUUID().toString()
    val annenBehandlingsid = UUID.randomUUID().toString()

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknader`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
            .shouldBeEmpty()
        sendSoknad(soknad)
        sendSoknad(
            soknad.copy(
                status = SoknadsstatusDTO.SENDT,
            ),
        )

        sendSoknad(annenSoknad)
        sendSoknad(
            annenSoknad.copy(
                status = SoknadsstatusDTO.SENDT,
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.count() == 2L
        }
        await().atMost(5, TimeUnit.SECONDS).until {
            sykepengesoknadRepository.count() == 2L
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at periodene venter på arbeidsgiver`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.plusSeconds(1).toInstant())
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
        val behandlingstatusmelding2 =
            Behandlingstatusmelding(
                vedtaksperiodeId = annenVedtaksperiodeId,
                behandlingId = annenBehandlingsid,
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(annenSoknad.id),
            )
        sendBehandlingsstatusMelding(behandlingstatusmelding2)
        sendBehandlingsstatusMelding(
            behandlingstatusmelding2.copy(
                status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
            ),
        )
        awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)
        awaitOppdatertStatus(
            VENTER_PÅ_ARBEIDSGIVER,
            vedtaksperiodeId = annenVedtaksperiodeId,
            behandlingId = annenBehandlingsid,
        )

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.plusSeconds(1).toInstant())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first() shouldBeEqualTo fnr

        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
            sendtTidspunkt.minusHours(3).toInstant(),
        ).shouldBeEmpty()
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
            "Status for sykefraværet som startet 29. mai 2022:\n" +
            "Vi venter på inntektsmelding fra Flex AS."

        val beskjedCR2 = brukerVarslinger.last().value().tilOpprettVarselInstance()
        beskjedCR2.eksternVarsling.shouldBeNull()
        beskjedCR2.tekster.first().tekst shouldBeEqualTo
            "Status for sykefraværet som startet 29. mai 2022:\n" +
            "Vi venter på inntektsmelding fra Kebabfabrikken."

        val meldinger = meldingKafkaConsumer.ventPåRecords(2)
        val meldingCR = meldinger.first()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo
            "Status for sykefraværet som startet 29. mai 2022:\n" +
            "Vi venter på inntektsmelding fra Flex AS."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()

        val opprettMeldingTo = objectMapper.readValue<MeldingKafkaDto>(meldinger.last().value())
        opprettMeldingTo.opprettMelding!!.tekst shouldBeEqualTo
            "Status for sykefraværet som startet 29. mai 2022:\n" +
            "Vi venter på inntektsmelding fra Kebabfabrikken."
    }
}
