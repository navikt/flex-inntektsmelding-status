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
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.tms.varsel.action.Sensitivitet
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
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
    fun `Sykmeldt sender inn sykepengesøknader, vi henter ut arbeidsgivers navn`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
            .shouldBeEmpty()
        sendSoknad(soknad)
        sendSoknad(
            soknad.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = LocalDateTime.now(),
            ),
        )

        sendSoknad(annenSoknad)
        sendSoknad(
            annenSoknad.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = LocalDateTime.now(),
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgnr2)?.navn == orgnavn2
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at periodene venter på arbeidsgiver`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
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
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(1)
        perioderSomVenterPaaArbeidsgiver.first() shouldBeEqualTo fnr

        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
            OffsetDateTime.now().minusHours(3).toInstant(),
        ).shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat[SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15] shouldBeEqualTo 1
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 1

        val brukerVarslinger = varslingConsumer.ventPåRecords(2)
        val beskjedCR = brukerVarslinger.first()
        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Vi venter på inntektsmeldingen fra Flex AS for sykefraværet som startet 29. mai 2022."

        val beskjedCR2 = brukerVarslinger.last().value().tilOpprettVarselInstance()
        beskjedCR2.eksternVarsling.shouldBeNull()
        beskjedCR2.tekster.first().tekst shouldBeEqualTo
            "Vi venter på inntektsmeldingen fra Kebabfabrikken for sykefraværet som startet 29. mai 2022."

        val meldinger = meldingKafkaConsumer.ventPåRecords(2)
        val meldingCR = meldinger.first()
        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo
            "Vi venter på inntektsmeldingen fra Flex AS for sykefraværet som startet 29. mai 2022."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()

        val opprettMeldingTo = objectMapper.readValue<MeldingKafkaDto>(meldinger.last().value())
        opprettMeldingTo.opprettMelding!!.tekst shouldBeEqualTo
            "Vi venter på inntektsmeldingen fra Kebabfabrikken for sykefraværet som startet 29. mai 2022."
    }
}
