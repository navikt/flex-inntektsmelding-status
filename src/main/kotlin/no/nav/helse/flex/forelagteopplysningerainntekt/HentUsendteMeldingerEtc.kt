package no.nav.helse.flex.forelagteopplysningerainntekt

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class HentUsendteMeldingerEtc(
    private val kombinerDataService: KombinerDataService,
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository
) {

    // Schedule the job to run at fixed intervals
    @Scheduled(fixedDelay = 60000) // Runs every 60 seconds (adjust as needed)
    fun runJob() {
            val usendteMeldingerEtc: List<ForelagteOpplysningerDbRecord>  = forelagteOpplysningerRepository.findAllByForelagtIsNull()

            val usendtMelding = usendteMeldingerEtc.firstOrNull()!!





            val kombinerteOpplysninger = kombinerDataService.mergeForelagteOpplysningerWithSykepengesoknad(usendtMelding.vedtaksperiodeId, usendtMelding.behandlingId)

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
