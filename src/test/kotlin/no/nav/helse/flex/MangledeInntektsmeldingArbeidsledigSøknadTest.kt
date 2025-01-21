package no.nav.helse.flex

import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MangledeInntektsmeldingArbeidsledigSøknadTest : FellesTestOppsett() {
    @Test
    @Order(0)
    fun `Sykmeldt sender inn arbeidsledig søknad`() {
        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.toInstant())
            .shouldBeEmpty()
        val soknad = Testdata.soknad.copy(type = SoknadstypeDTO.ARBEIDSLEDIG, arbeidsgiver = null)
        sendSoknad(soknad)
        sendSoknad(
            soknad.copy(
                status = SoknadsstatusDTO.SENDT,
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            sykepengesoknadRepository.count() == 1L
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
    fun `Første mangler inntektsmelding varsel`() {
        varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(15).plusMinutes(1))

        meldingKafkaConsumer.ventPåRecords(1)
        val varslingRecords = varslingConsumer.ventPåRecords(1)

        val beskjedOpprettVarsel = varslingRecords.last()
        val beskjedInput = beskjedOpprettVarsel.value().tilOpprettVarselInstance()
        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Status for sykefraværet som startet 29. mai 2022: " +
            "Vi venter på inntektsmelding fra arbeidsgiver."
    }

    @Test
    @Order(3)
    fun `Andre mangler inntektsmelding varsel`() {
        varselutsendingCronJob.runMedParameter(sendtTidspunkt.plusDays(32))

        awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)

        val varslingRecords = varslingConsumer.ventPåRecords(2)
        meldingKafkaConsumer.ventPåRecords(2)

        val beskjedOpprettVarsel = varslingRecords.last()
        val beskjedInput = beskjedOpprettVarsel.value().tilOpprettVarselInstance()

        @Suppress("ktlint:standard:max-line-length")
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Status for sykefraværet som startet 29. mai 2022: " +
            "Saksbehandlingen er forsinket fordi vi fortsatt venter på inntektsmelding fra arbeidsgiver."
    }
}
