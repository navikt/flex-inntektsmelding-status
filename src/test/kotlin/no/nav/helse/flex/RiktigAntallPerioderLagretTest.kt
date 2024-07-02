package no.nav.helse.flex

import no.nav.helse.flex.Testdata.behandlingId
import no.nav.helse.flex.Testdata.fnr
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.soknad
import no.nav.helse.flex.Testdata.soknadId
import no.nav.helse.flex.Testdata.vedtaksperiodeId
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RiktigAntallPerioderLagretTest : FellesTestOppsett() {
    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()
        sendSoknad(soknad)
        sendSoknad(
            soknad.copy(
                status = SoknadsstatusDTO.SENDT,
                sendtNav = LocalDateTime.now(),
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at perioden venter på arbeidsgiver`() {
        vedtaksperiodeBehandlingRepository.finnPersonerMedPerioderSomVenterPaaArbeidsgiver(Instant.now()).shouldBeEmpty()

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

        awaitOppdatertStatus(OPPRETTET)

        val newBehandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = "3be706ee-b1b3-4558-b370-c1cd15429e5a",
                behandlingId = "64c03649-a3b5-49df-bae2-0bd1f838b5a1",
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknadId),
            )

        sendBehandlingsstatusMelding(newBehandlingstatusmelding)

        awaitOppdatertStatus(OPPRETTET, newBehandlingstatusmelding.vedtaksperiodeId, newBehandlingstatusmelding.behandlingId)

        val perioder = hentAltForPerson.hentAltForPerson(fnr)
        perioder.shouldHaveSize(2)
    }
}
