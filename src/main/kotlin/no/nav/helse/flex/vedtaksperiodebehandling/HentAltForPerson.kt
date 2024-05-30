package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class HentAltForPerson(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun hentAltForPerson(fnr: String): List<FullVedtaksperiodeBehandling> {
        val soknader = sykepengesoknadRepository.findByFnr(fnr)

        // dette henter ut en liste med vedtaksperioder og sykepengesoknader ider
        // var vedtaksperiodeBehandlingerSykepengesoknad  = vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(soknader.map { it.sykepengesoknadUuid })
        val vedtaksperiodeBehandlingerSykepengesoknad: List<VedtaksperiodeBehandlingSykepengesoknadDbRecord> =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(
                soknader.map {
                    it.sykepengesoknadUuid
                },
            )

        // denne henter ut
        val vedtaksperiodeBehandlinger: List<VedtaksperiodeBehandlingDbRecord> =
            vedtaksperiodeBehandlingRepository.findByIdIn(
                vedtaksperiodeBehandlingerSykepengesoknad.map {
                    it.vedtaksperiodeBehandlingId
                },
            )

        // val vedtaksperiodeBehandlingStatuser : List<VedtaksperiodeBehandlingStatusDbRecord> = vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(vedtaksperiodeBehandlinger.map { it.id!! })

        var fullSoknadsBehandlingListe = mutableListOf<FullVedtaksperiodeBehandling>()

        for (vedtaksperiodeBehandling in vedtaksperiodeBehandlinger) {
            val vedtaksperiodeId = vedtaksperiodeBehandling.id
            val soknaderIder =
                vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingId(vedtaksperiodeId!!).map {
                    it.sykepengesoknadUuid
                }

            val soknaderForPeriode: List<Sykepengesoknad> = sykepengesoknadRepository.findBySykepengesoknadUuidIn(soknaderIder)

            val statuser: List<VedtaksperiodeBehandlingStatusDbRecord> =
                vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(
                    listOf(vedtaksperiodeId),
                )

            val objekt = FullVedtaksperiodeBehandling(vedtaksperiodeBehandling, soknaderForPeriode, statuser)

            fullSoknadsBehandlingListe.add(objekt)
        }

        return fullSoknadsBehandlingListe
    }
}

data class FullVedtaksperiodeBehandling(
    val vedtaksperiode: VedtaksperiodeBehandlingDbRecord,
    val soknader: List<Sykepengesoknad>,
    val statuser: List<VedtaksperiodeBehandlingStatusDbRecord>,
)

// data class FullVedtaksperiodeBehandling(
//    val soknadBehandling: FullVedtaksperiodeBehandling,
// )
//
// data class FullVedtaksperiodeBehandling(
//    val soknad: Sykepengesoknad,
//    val vedtaksperioder: List<FullVedtaksperiodeBehandling>,
// )

/*
FullVedtaksperiodeBehandling(


    soknad=Sykepengesoknad(
        id=bd532141-a736-4933-b87f-c29545d10168,
        sykepengesoknadUuid=7c1519b5-fb94-4972-96a8-a9bca27a7b69,
        orgnummer=123456547,
        soknadstype=ARBEIDSTAKERE,
        startSyketilfelle=2022-06-01,
        fom=2022-06-01,
        tom=2022-06-30,
        fnr=12345678901,
        sendt=2024-05-28T12:17:52.663623Z,
        opprettetDatabase=2024-05-28T12:17:52.859858Z
    ),
    vedtaksperioder=[
        VedtaksperiodeMedStatuser(
            vedtaksperiode=VedtaksperiodeBehandlingDbRecord(
                id=d6b9483a-a1b1-4653-964f-b258385c5621,
                opprettetDatabase=2024-05-28T12:17:54.965032Z,
                oppdatert=2024-05-28T12:17:54.989932Z,
                sisteSpleisstatus=VENTER_PÅ_ARBEIDSGIVER,
                sisteVarslingstatus=null,
                vedtaksperiodeId=393dfb2f-5fc5-439c-8ff1-e0f003c9b90b,
                behandlingId=b523c2ce-017d-4f17-a6d2-97719c42b1ef,
                sykepengesoknadUuid=7c1519b5-fb94-4972-96a8-a9bca27a7b69
            ),
            status=[
                VedtaksperiodeBehandlingStatusDbRecord(
                    id=ec2a6dea-bee0-475c-88da-47460c6894c9,
                    vedtaksperiodeBehandlingId=d6b9483a-a1b1-4653-964f-b258385c5621,
                    opprettetDatabase=2024-05-28T12:17:54.972626Z,
                    tidspunkt=2024-05-28T12:17:52.909689Z,
                    status=OPPRETTET,
                    brukervarselId=null,
                    dittSykefravaerMeldingId=null
                ),
                VedtaksperiodeBehandlingStatusDbRecord(
                    id=fdf3fc23-0dc4-467c-8b9b-8b2b1ff7236b,
                    vedtaksperiodeBehandlingId=d6b9483a-a1b1-4653-964f-b258385c5621,
                    opprettetDatabase=2024-05-28T12:17:54.995688Z,
                    tidspunkt=2024-05-28T12:17:52.909689Z,
                    status=VENTER_PÅ_ARBEIDSGIVER,
                    brukervarselId=null,
                    dittSykefravaerMeldingId=null
                )
            ]
        )

*/
