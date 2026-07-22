package no.nav.helse.flex

import no.nav.helse.flex.Testdata.sendtTidspunkt
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.SlettSpeilstatuserJob
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.*
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusDbRecord
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainSame
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.concurrent.TimeUnit

class SlettSpeilstatuserJobTest : FellesTestOppsett() {
    @Autowired
    lateinit var slettSpeilstatuserJob: SlettSpeilstatuserJob

    @BeforeEach
    fun beforeEach() {
        slettFraDatabase()
    }

    private val speilstatuser =
        listOf(
            OPPRETTET,
            VENTER_PÅ_ARBEIDSGIVER,
            VENTER_PÅ_SAKSBEHANDLER,
            VENTER_PÅ_ANNEN_PERIODE,
            FERDIG,
            BEHANDLES_UTENFOR_SPEIL,
        )

    private val varslingsstatuser =
        listOf(
            VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
            VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
            REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
        )

    @Test
    fun `sletter kun speilstatuser og lar varslingsstatuser være i fred`() {
        val behandlingId = opprettBehandling()

        (speilstatuser + varslingsstatuser).forEach { lagreStatus(behandlingId, it) }

        val antallSlettet = slettSpeilstatuserJob.run()

        antallSlettet shouldBeEqualTo speilstatuser.size

        vedtaksperiodeBehandlingStatusRepository
            .findByVedtaksperiodeBehandlingIdIn(listOf(behandlingId))
            .map { it.status } shouldContainSame varslingsstatuser
    }

    @Test
    fun `sletter i batcher til det ikke er mer å slette`() {
        val behandlingId = opprettBehandling()

        val antallSpeilstatuser = 12_000
        repeat(antallSpeilstatuser) { lagreStatus(behandlingId, OPPRETTET) }

        val antallSlettet = slettSpeilstatuserJob.run()

        antallSlettet shouldBeEqualTo antallSpeilstatuser
        vedtaksperiodeBehandlingStatusRepository
            .findByVedtaksperiodeBehandlingIdIn(listOf(behandlingId))
            .shouldBeEmpty()
    }

    private fun opprettBehandling(): String {
        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = Testdata.vedtaksperiodeId,
                behandlingId = Testdata.behandlingId,
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = sendtTidspunkt,
                eksterneSøknadIder = listOf(Testdata.soknadId),
            )
        sendSoknad(Testdata.soknad)
        sendSoknad(Testdata.soknad.copy(status = SoknadsstatusDTO.SENDT))
        await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(Testdata.orgNr)?.navn == "Flex AS"
        }
        sendBehandlingsstatusMelding(behandlingstatusmelding)
        sendBehandlingsstatusMelding(behandlingstatusmelding.copy(status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER))

        return awaitOppdatertStatus(VENTER_PÅ_ARBEIDSGIVER).id!!
    }

    private fun lagreStatus(
        behandlingId: String,
        status: StatusVerdi,
    ) {
        vedtaksperiodeBehandlingStatusRepository.save(
            VedtaksperiodeBehandlingStatusDbRecord(
                vedtaksperiodeBehandlingId = behandlingId,
                opprettetDatabase = Instant.now(),
                tidspunkt = Instant.now(),
                status = status,
                brukervarselId = null,
                dittSykefravaerMeldingId = null,
            ),
        )
    }
}
