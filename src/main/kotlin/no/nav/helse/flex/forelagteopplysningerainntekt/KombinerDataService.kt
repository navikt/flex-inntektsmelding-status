package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.ForelagteOpplysningerRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.apache.kafka.common.message.ListOffsetsRequestData.ListOffsetsTopic
import org.springframework.data.annotation.Id
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

// what if you just took fnr and looked directly at sykepengesoknad using fnr?
@Service
class KombinerDataService(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository
) {

    fun mergeForelagteOpplysningerWithSykepengesoknad(forelagteOpplysninger : ForelagteOpplysningerDbRecord): List<KombinerteData> {


        /*
        @Table(value = "forelagte_opplysninger_ainntekt")
public final data class ForelagteOpplysningerDbRecord(
    val id: String? = null,
    val fnr: String? = null,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val forelagteOpplysningerMelding: PGobject,
    val opprettet: Instant,
    val forelagt: Instant?
)
         */
        val fnr = forelagteOpplysninger.fnr


        val vedtaksperiodeBehandlingerViaVeBeIder = vedtaksperiodeBehandlingSykepengesoknadRepository.findByVedtaksperiodeBehandlingIdIn(listOf(forelagteOpplysninger.behandlingId)).map(sykepengesoknadRepository)


        val sykepengesoknader = if (fnr != null) sykepengesoknadRepository.findByFnr(fnr) else emptyList()

        val vedtaksperiodeBehandlinger = if (sykepengesoknader.isNotEmpty()) vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(sykepengesoknader.map { it.sykepengesoknadUuid}) else emptyList()







        /*
        @Table(value = "vedtaksperiode_behandling")
public final data class VedtaksperiodeBehandlingDbRecord(
    val id: String? = null,
    val opprettetDatabase: Instant,
    val oppdatertDatabase: Instant,
    val sisteSpleisstatus: StatusVerdi,
    val sisteSpleisstatusTidspunkt: Instant,
    val sisteVarslingstatus: StatusVerdi?,
    val sisteVarslingstatusTidspunkt: Instant?,
    val vedtaksperiodeId: String,
    val behandlingId: String
)
         */
        /
        /*
            data class Sykepengesoknad(
                @Id
    val id: String? = null,
                val sykepengesoknadUuid: String,
                val orgnummer: String?,
                val soknadstype: String,
                val startSyketilfelle: LocalDate,
                val fom: LocalDate,
                val tom: LocalDate,
                val fnr: String,
                val sendt: Instant,
                val opprettetDatabase: Instant,
)
*/
//
//
//            val orgnr = sykepengesoknader.map{ it.orgnummer }.distinct()
//
//            return sykepengesoknader.map { sykepengesoknad ->
//                KombinerteData(
//                    fnr = opplysning.fnr,
//                    orgnummer = orgnr,
//                    opplysning = opplysning,
//                    behandling = behandlingRecord,
//                    sykepengesoknad = sykepengesoknad
//                )
//            }
//        } else {
//            return emptyList()
//        }
//    }
    }
}

data class KombinerteData(
    val fnr: String?,
    val orgnummer: List<String?>,
    val opplysning: ForelagteOpplysningerDbRecord,
    val behandling: VedtaksperiodeBehandlingDbRecord,
    val sykepengesoknad: Sykepengesoknad
)
