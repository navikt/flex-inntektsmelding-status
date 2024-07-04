package no.nav.helse.flex

import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.fom
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.tom
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.varselutsending.CronJobStatus.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.VENTER_PÅ_ARBEIDSGIVER
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ThrottleForsinketSaksbehandlingTest : FellesTestOppsett() {
    @Test
    @Order(1)
    fun `Vi sender inn 6 søknader som venter på saksbehandler`() {
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

            sendInntektsmelding(
                skapInntektsmelding(
                    fnr = soknaden.fnr,
                    virksomhetsnummer = soknaden.arbeidsgiver!!.orgnummer,
                    refusjonBelopPerMnd = BigDecimal(5000),
                    beregnetInntekt = BigDecimal(10000),
                    vedtaksperiodeId = behandlingstatusmelding.vedtaksperiodeId,
                ),
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
                StatusVerdi.VENTER_PÅ_SAKSBEHANDLER,
                behandlingId = behandlingstatusmelding.behandlingId,
                vedtaksperiodeId = behandlingstatusmelding.vedtaksperiodeId,
            )
        }

        (0 until 6).forEach { index -> sendSoknad(index) }

        val perioderSomVenterPaaArbeidsgiver =
            vedtaksperiodeBehandlingRepository.finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(Instant.now())
        perioderSomVenterPaaArbeidsgiver.shouldHaveSize(6)
    }

    @Test
    @Order(2)
    fun `Vi sender ut mangler  varsel etter 30 dager`() {
        val cronjobResultat = varselutsendingCronJob.runMedParameter(OffsetDateTime.now().plusDays(30))
        cronjobResultat[SENDT_VARSEL_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 4
        cronjobResultat[UNIKE_FNR_KANDIDATER_MANGLENDE_INNTEKTSMELDING_15] shouldBeEqualTo 0
        cronjobResultat[UNIKE_FNR_KANDIDATER_FORSINKET_SAKSBEHANDLING_28] shouldBeEqualTo 6
        cronjobResultat[UTELATTE_FNR_FORSINKET_SAKSBEHANDLING_THROTTLE] shouldBeEqualTo 2
        varslingConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
        meldingKafkaConsumer.ventPåRecords(4, duration = Duration.ofMinutes(1))
    }
}
