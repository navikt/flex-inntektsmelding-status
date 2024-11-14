package no.nav.helse.flex

import no.nav.helse.flex.Testdata.behandlingId
import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.Testdata.soknad
import no.nav.helse.flex.Testdata.soknadId
import no.nav.helse.flex.Testdata.vedtaksperiodeId
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.util.tilOsloLocalDateTime
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.VENTER_PÅ_ARBEIDSGIVER
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.VENTER_PÅ_SAKSBEHANDLER
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KorrigertSoknadVarslingTest : FellesTestOppsett() {
    val korrigerendeSoknadId = UUID.randomUUID().toString()

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad og korrigerer den`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now())
            .shouldBeEmpty()
        sendSoknad(soknad)
        sendSoknad(
            soknad.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = sendtTidspunkt.tilOsloLocalDateTime(),
                sendtArbeidsgiver = sendtTidspunkt.tilOsloLocalDateTime(),
            ),
        )
        sendSoknad(
            soknad.copy(
                id = korrigerendeSoknadId,
                status = SoknadsstatusDTO.SENDT,
                korrigerer = soknadId,
                sendtNav = sendtTidspunkt.plusDays(18).tilOsloLocalDateTime(),
                sendtArbeidsgiver = sendtTidspunkt.plusDays(18).tilOsloLocalDateTime(),
            ),
        )
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at perioden venter på arbeidsgiver`() {
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

        awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER)

        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.plusMinutes(1).toInstant())
            .shouldHaveSize(1)

        vedtaksperiodeBehandlingRepository
            .finnPersonerMedPerioderSomVenterPaaArbeidsgiver(sendtTidspunkt.plusDays(18).plusMinutes(1).toInstant())
            .shouldHaveSize(1)
    }

    @Test
    @Order(2)
    fun `Vi får beskjed at perioden venter på saksbehandling og lenker den til korrigeringen`() {
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

        awaitOppdatertStatus(VENTER_PÅ_SAKSBEHANDLER)

        vedtaksperiodeBehandlingRepository
            .finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(sendtTidspunkt.plusDays(29).toInstant())
            .shouldHaveSize(1)

        vedtaksperiodeBehandlingRepository
            .finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(sendtTidspunkt.plusDays(1).toInstant())
            .shouldHaveSize(0)
    }
}
