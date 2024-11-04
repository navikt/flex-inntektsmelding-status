package no.nav.helse.flex.forelagteopplysningerainntekt


import no.nav.helse.flex.organisasjon.OrganisasjonRepository
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.postgresql.util.PGobject
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class HentUsendteMeldingerEtc(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val organisasjonRepository: OrganisasjonRepository,
) {
// Lazy loading usendteMeldingerEtc to ensure it fetches from repository when accessed
    val usendteMeldingerEtc: List<ForelagteOpplysningerDbRecord>
        get() = forelagteOpplysningerRepository.findAllByForelagtIsNull()

    fun processFnrItems(usendteMeldingerEtc: List<ForelagteOpplysningerDbRecord>) : List<String?> {
        val fnr = usendteMeldingerEtc.map { it.fnr }

        for (fnrItem in fnr) {
            // Perform actions with fnrItem
            println(fnrItem) // Example action: print each item
        }

        return fnr
    }

    fun findSykepengesoknader(fnr : String) : List<String?> {
            val sykepengesoknader = sykepengesoknadRepository.findByFnr(fnr!!)
            val orgnrs = sykepengesoknader.map { it.orgnummer }
           return orgnrs

    }


    // todo du må finne ut om det er et varsel på orgnr og fødselsnr de siste n dager/uker/mnd

    //val sykepengesoknader = sykepengesoknadRepository.findByFnr(fnr)

    // val orgnr = sykepengesoknader.map { it.orgnummer}

    val fnrMedUsendtMelding = processFnrItems(usendteMeldingerEtc)



    data class FnrOrgnrPair(val fnr: String?, val orgnr: List<String?>)

    val orgnrPerFnrObject: List<FnrOrgnrPair> = fnrMedUsendtMelding.map { fnrItem ->
        val orgnrs = findSykepengesoknader(fnrItem!!) // kan jeg hevde dette?
        FnrOrgnrPair(fnr = fnrItem, orgnr = orgnrs)
    }

    // val meldingerToSend : List<ForelagteOpplysningerDbRecord> =

    data class KombinertDataObjekt (
        // from forelagte opplysninger:
        val fnr: String? = null,
        val vedtaksperiodeId: String,
        val behandlingId: String,
        val forelagteOpplysningerMelding: PGobject,
        val opprettet: Instant,
        val forelagt: Instant?,
        val sykmeldingId: String,
        val sykepengesoknad: Sykepengesoknad,
    )


    // bruk fnr til å finne sykepengesoknader

    /*

    chat gpt!!! this is your task:
    # the first table I need to make
    I think I need to merge these so that I have:

    from forelagte opplysninger:
    val fnr: String? = null,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val forelagteOpplysningerMelding: PGobject,
    val opprettet: Instant,
    val forelagt: Instant?,


    from sykepengesoknad:
    val sykepengesoknadUuid: String,
    val orgnummer: String?,


    once i have this table I can check if any previous notifications exist recently and decide wether to send the message or not



@Table("forelagte_opplysninger_ainntekt")
data class ForelagteOpplysningerDbRecord(
    @Id
    val id: String? = null,
    val fnr: String? = null,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val forelagteOpplysningerMelding: PGobject,
    val opprettet: Instant,
    val forelagt: Instant?,
)


    @Table("vedtaksperiode_behandling")
data class VedtaksperiodeBehandlingDbRecord(
    @Id
    val id: String? = null,
    val opprettetDatabase: Instant,
    val oppdatertDatabase: Instant,
    val sisteSpleisstatus: StatusVerdi,
    val sisteSpleisstatusTidspunkt: Instant,
    val sisteVarslingstatus: StatusVerdi?,
    val sisteVarslingstatusTidspunkt: Instant?,
    val vedtaksperiodeId: String,
    val behandlingId: String,
)

@Table("vedtaksperiode_behandling_sykepengesoknad")
data class VedtaksperiodeBehandlingSykepengesoknadDbRecord(
    @Id
    val id: String? = null,
    val vedtaksperiodeBehandlingId: String,
    val sykepengesoknadUuid: String,
)

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

@Repository
interface SykepengesoknadRepository : CrudRepository<Sykepengesoknad, String> {
    fun findBySykepengesoknadUuid(sykepengesoknadUuid: String): Sykepengesoknad?

    fun findBySykepengesoknadUuidIn(sykepengesoknadUuid: List<String>): List<Sykepengesoknad>

    fun findByFnr(fnr: String): List<Sykepengesoknad>
}


     */


}
