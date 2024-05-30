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
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
) {
    
    
    
    fun hentStatuserOgSoknad(sykepengesoknadUuid : String, fnr : String): FullSoknadBehandling {
        val soknader = sykepengesoknadRepository.findBySykepengesoknadUuid(sykepengesoknadUuid)
        
        var returnValue =
            soknader.map { soknad ->
                val vedtaksperiodeBehandlingerMedStatus =
                    vedtaksperiodeBehandlinger
                        .filter { it.vedtaksperiodeId == sykepengesoknadToVedtaksperiodeMap[soknad.sykepengesoknadUuid] }
                        .map { FullVedtaksperiodeBehandling(it, statuser.filter { status -> status.vedtaksperiodeBehandlingId == it.id }) }
                FullSoknadBehandling(soknad, vedtaksperiodeBehandlingerMedStatus)
            }

        return returnValue
    }
    
    
    
    @Transactional(propagation = Propagation.REQUIRED)
    fun hentAltForPerson(fnr: String): List<FullSoknadBehandling> {
        /*
        ublic final data class Sykepengesoknad(
    val id: String? = null,
    val sykepengesoknadUuid: String,
    val orgnummer: String?,
    val soknadstype: String,
    val startSyketilfelle: LocalDate,
    val fom: LocalDate,
    val tom: LocalDate,
    val fnr: String,
    val sendt: Instant,
    val opprettetDatabase: Instant
)
         */

        val soknader = sykepengesoknadRepository.findByFnr(fnr)
        var vedtaksperiodeBehandlingerIder = setOf<String>()
        var vedtaksperiodeBehandlingerIderToSykepengesoknadUuidMap = mutableMapOf<String, Set<String>>()
        var sykepengesoknadToVedtaksperiodeMap = mutableMapOf<String, String>()
        // val vedtaksperiodeBehandlingId = VedtaksperiodeBehandlingSykepengesoknadDbRecord.findBySykepengesoknadUuid(soknader.map { it.sykepengesoknadUuid })

        for (soknad in soknader) {
            val vedtaksperiodeBehandlingerForSoknad =
                vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuid(
                    soknad.sykepengesoknadUuid,
                )
            val vedtaksperiodeBehandlingerIderStrings = vedtaksperiodeBehandlingerForSoknad.map { it.vedtaksperiodeBehandlingId }
            vedtaksperiodeBehandlingerIder = vedtaksperiodeBehandlingerIder.union(vedtaksperiodeBehandlingerIderStrings)
            vedtaksperiodeBehandlingerIderToSykepengesoknadUuidMap[soknad.sykepengesoknadUuid] =
                vedtaksperiodeBehandlingerForSoknad.map {
                    it.vedtaksperiodeBehandlingId
                }.toSet()
            sykepengesoknadToVedtaksperiodeMap[soknad.sykepengesoknadUuid] =
                vedtaksperiodeBehandlingerForSoknad.map {
                    it.vedtaksperiodeBehandlingId
                }.first()

            // sykepengesoknadForVedtaksperiodeMap[soknad.sykepengesoknadUuid] = sykepengesoknadForVedtaksperiodeMap[soknad.sykepengesoknadUuid].union()
        }

        val vedtaksperiodeBehandlinger =
            vedtaksperiodeBehandlingRepository.findBySykepengesoknadUuidIn(soknader.map { it.sykepengesoknadUuid })
        val statuser =
            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(vedtaksperiodeBehandlinger.map { it.id!! })

        //  vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(soknader.map { it.sykepengesoknadUuid })
        // todo du må ta en liste av vedtaksperiodebehandlinger her også ... så får ale disse denne listen av soknader med innhold ... tidligere var det antatt at det var en til en

        // vedtaksperiodeBehandlinger
        val returnValue =
            soknader.map { soknad ->
                val vedtaksperiodeBehandlingerMedStatus =
                    vedtaksperiodeBehandlinger
                        .filter { it.vedtaksperiodeId == sykepengesoknadToVedtaksperiodeMap[soknad.sykepengesoknadUuid] }
                        .map { FullVedtaksperiodeBehandling(it, statuser.filter { status -> status.vedtaksperiodeBehandlingId == it.id }) }
                FullSoknadBehandling(soknad, vedtaksperiodeBehandlingerMedStatus)
            }

        return returnValue
    }
}

//    @Transactional(propagation = Propagation.REQUIRED)
//    fun hentAltForPerson(fnr: String): List<FullVedtaksperiodeBehandling> {
//        val soknader = sykepengesoknadRepository.findByFnr(fnr)
//
//        val vedtaksperiodeBehandlinger =
//            vedtaksperiodeBehandlingRepository.findBySykepengesoknadUuidIn(soknader.map { it.sykepengesoknadUuid })
//        val statuser =
//            vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(vedtaksperiodeBehandlinger.map { it.id!! })
//
//        return soknader.map { soknad ->
//            val vedtaksperiodeBehandlingerMedStatus =
//                vedtaksperiodeBehandlinger
//                    // .filter { it.sykepengesoknadUuid == soknad.sykepengesoknadUuid } // todo her må vi gjøre noe, men hva? sende inn en liste
//                    .any { it.vedtaksperiodeId == soknad.sykepengesoknadUuid } // todo her må vi gjøre noe, men hva? sende inn en liste
//                    .map { VedtaksperiodeMedStatuser(it, statuser.filter { status -> status.vedtaksperiodeBehandlingId == it.id }) }
//            FullVedtaksperiodeBehandling(soknad, vedtaksperiodeBehandlingerMedStatus)
//        }
//    }
//

    /*

    // TODO dette er det nåværende resultatet, nå som vi har flere sykepengesoknader per vedtaksperiode, bør vi gå i en annen retning? vedtaksperioder på topp?N

    // det blir en array av soknader for en vedtaksperiode
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
    ]
)



     */

// @Transactional(propagation = Propagation.REQUIRED)
// fun hentAltForPerson(fnr: String): List<FullVedtaksperiodeBehandling> {
//    val soknader = sykepengesoknadRepository.findByFnr(fnr)
//    val soknadUuids = soknader.map { it.sykepengesoknadUuid }
//
//    // Finn vedtaksperiodeBehandlinger basert på sykepengesoknadUuid
//    val behandlingSoknadRecords = vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(soknadUuids)
//    val behandlingIds = behandlingSoknadRecords.map { it.vedtaksperiodeBehandlingId }
//
//    // Lag en hashmap med sykepengesoknadUuid som nøkkel og en liste av VedtaksperiodeBehandlingSykepengesoknadDbRecord som verdi
//    val behandlingSoknadMap = behandlingSoknadRecords.groupBy { it.sykepengesoknadUuid }
//
//    // Finn vedtaksperiodeBehandlinger
//    val vedtaksperiodeBehandlinger = vedtaksperiodeBehandlingRepository.findAllById(behandlingIds)
//
//    // Lag en hashmap med vedtaksperiodeBehandlingId som nøkkel og VedtaksperiodeBehandlingDbRecord som verdi
//    val vedtaksperiodeBehandlingMap = vedtaksperiodeBehandlinger.associateBy { it.id }
//
//    // Finn statuser for behandlingene
//    val statuser = vedtaksperiodeBehandlingStatusRepository.findByVedtaksperiodeBehandlingIdIn(behandlingIds)
//
//    // Lag en hashmap med vedtaksperiodeBehandlingId som nøkkel og en liste av VedtaksperiodeBehandlingStatusDbRecord som verdi
//    val statusMap = statuser.groupBy { it.vedtaksperiodeBehandlingId }
//
//    return soknader.map { soknad ->
//        val behandlingerForSoknad = behandlingSoknadMap[soknad.sykepengesoknadUuid] ?: emptyList()
//        val vedtaksperiodeBehandlingerMedStatus = behandlingerForSoknad.mapNotNull { behandlingSoknad ->
//            val behandling = vedtaksperiodeBehandlingMap[behandlingSoknad.vedtaksperiodeBehandlingId]
//            if (behandling != null) {
//                VedtaksperiodeMedStatuser(behandling, statusMap[behandling.id] ?: emptyList())
//            } else null
//        }
//        FullVedtaksperiodeBehandling(soknad, vedtaksperiodeBehandlingerMedStatus)
//    }
// }

data class FullVedtaksperiodeBehandling(
    val soknadBehandling: FullSoknadBehandling,
)

data class FullSoknadBehandling(
    val soknad: Sykepengesoknad,
    val vedtaksperioder: List<FullVedtaksperiodeBehandling>,
)


data class FullVedtaksperiodeBehandling(
    val vedtaksperiode: VedtaksperiodeBehandlingDbRecord,
    val soknader: List<Sykepengesoknad>,
    val status: List<VedtaksperiodeBehandlingStatusDbRecord>,
)

