package no.nav.helse.flex

import no.nav.helse.flex.Testdata.fom
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.tom
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.VENTER_PÅ_ARBEIDSGIVER
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ThrottleVarsleTest : FellesTestOppsett() {
    @Test
    @Order(1)
    fun `Vi sender inn 20 søknader som venter på arbeidsgiver`() {
        fun sendSoknad(index: Int) {
            val soknaden =
                SykepengesoknadDTO(
                    fnr = (10000000000 + index).toString(),
                    id = UUID.randomUUID().toString(),
                    type = SoknadstypeDTO.ARBEIDSTAKERE,
                    status = SoknadsstatusDTO.NY,
                    startSyketilfelle = fom,
                    fom = fom,
                    tom = tom,
                    arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
                    arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr),
                )
            sendSoknad(soknaden)
            sendSoknad(
                soknaden.copy(
                    status = SoknadsstatusDTO.SENDT,
                    sendtNav = LocalDateTime.now(),
                ),
            )

            await().atMost(5, TimeUnit.SECONDS).until {
                sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id) != null
            }

            val tidspunkt = OffsetDateTime.now()
            val behandlingstatusmelding =
                Behandlingstatusmelding(
                    vedtaksperiodeId = UUID.randomUUID().toString(),
                    behandlingId = UUID.randomUUID().toString(),
                    status = Behandlingstatustype.OPPRETTET,
                    tidspunkt = tidspunkt,
                    eksterneSøknadIder = listOf(soknaden.id),
                )
            sendBehandlingsstatusMelding(behandlingstatusmelding)

            sendBehandlingsstatusMelding(
                behandlingstatusmelding.copy(
                    status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
                ),
            )

            awaitOppdatertStatus(
                VENTER_PÅ_ARBEIDSGIVER,
                behandlingId = behandlingstatusmelding.behandlingId,
                vedtaksperiodeId = behandlingstatusmelding.vedtaksperiodeId,
            )
        }

        (0 until 45).forEach { index -> sendSoknad(index) }

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(45)
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler inntektsmelding varsel etter 15 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(16))
        cronjobResultat[SENDT_VARSEL_MANGLER_INNTEKTSMELDING_15] shouldBeEqualTo 20
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 45
        cronjobResultat[UTELATTE_FNR_MANGLER_IM_15_THROTTLE] shouldBeEqualTo 25
        varslingConsumer.ventPåRecords(20, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(20, duration = Duration.ofMinutes(1))
    }
}
