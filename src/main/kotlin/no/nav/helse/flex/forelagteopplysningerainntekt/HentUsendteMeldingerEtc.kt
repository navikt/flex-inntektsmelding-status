package no.nav.helse.flex.forelagteopplysningerainntekt

// import no.nav.helse.flex.vedtaksperiodebehandling.ForelagteOpplysningerRepository
import no.nav.helse.flex.vedtaksperiodebehandling.ForelagteOpplysningerRepository
import no.nav.helse.flex.vedtaksperiodebehandling.HentAltForPerson
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class HentUsendteMeldingerEtc(
    private val kombinerDataService: KombinerDataService,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val hentAltForPerson: HentAltForPerson,
) {
    // Schedule the job to run at fixed intervals
    @Scheduled(fixedDelay = 60000) // Runs every 60 seconds (adjust as needed)
    fun runJob() {
        // vedtaksperiodeId er for en periode fom tom, behandlingId er for en vedtaksperiode
        val usendteMeldingerEtc: List<ForelagteOpplysningerDbRecord> = forelagteOpplysningerRepository.findAllByForelagtIsNull()
        val sendteMeldinger: List<ForelagteOpplysningerDbRecord> = forelagteOpplysningerRepository.findAllByForelagtIsNotNull()

        for (usendtMelding in usendteMeldingerEtc) {
            val fnr = usendtMelding.fnr

            val sendteMeldinger = sendteMeldinger.filter { it.fnr == fnr }

            if (fnr != null) {
                val altForPerson = hentAltForPerson.hentAltForPerson(fnr)
            }
        }

            /*
            finn det relevante orgnr:
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

        // val kombinerteOpplysninger = kombinerDataService.mergeForelagteOpplysningerWithSykepengesoknad(usendtMelding.vedtaksperiodeId, usendtMelding.behandlingId)

            /*
        public final data class KombinerteData(
    val opplysninger: ForelagteOpplysningerDbRecord,
    val behandling: VedtaksperiodeBehandlingDbRecord,
    val sykepengesoknad: Sykepengesoknad
)
             */

        val fourWeeksAgo = Instant.now().minus(28, ChronoUnit.DAYS)

        for (item in kombinerteOpplysninger) {
            item.opplysninger.forelagt?.let { forelagt ->
                if (forelagt.isBefore(fourWeeksAgo)) {
                    println("True: forelagt timestamp er eldre enn 4 mnd ID: ${item.opplysninger.id}")
                }
            }
        }
    }
}
