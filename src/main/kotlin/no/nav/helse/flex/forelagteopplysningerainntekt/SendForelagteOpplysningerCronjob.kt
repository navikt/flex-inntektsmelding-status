package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.TotaltAntallForelagteOpplysningerSjekk
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.tilOsloZone
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.*
import java.util.concurrent.TimeUnit

class SendForelagteOpplysningerCronjobResultat(
    val antallForelagteOpplysningerSendt: Int = 0,
    val antallForelagteOpplysningerHoppetOver: Int = 0,
)

@Profile("forelagteopplysninger")
@Component
class SendForelagteOpplysningerCronjob(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val sendForelagteOpplysningerOppgave: SendForelagteOpplysningerOppgave,
    private val totaltAntallForelagteOpplysningerSjekk: TotaltAntallForelagteOpplysningerSjekk,
) {
    private val log = logger()

    @Scheduled(initialDelay = 10, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun run(): SendForelagteOpplysningerCronjobResultat {
        val osloDatetimeNow = OffsetDateTime.now().tilOsloZone()
        if (osloDatetimeNow.dayOfWeek in setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)) {
            log.info("Det er helg, jobben kjøres ikke")
            return SendForelagteOpplysningerCronjobResultat()
        }
        if (osloDatetimeNow.hour < 9 || osloDatetimeNow.hour > 15) {
            log.info("Det er ikke dagtid, jobben kjøres ikke")
            return SendForelagteOpplysningerCronjobResultat()
        }

        return runMedParameter(osloDatetimeNow.toInstant())
    }

    fun runMedParameter(now: Instant): SendForelagteOpplysningerCronjobResultat {
        log.info("Starter ${this::class.simpleName}")

        val usendteForelagteOpplysninger: List<ForelagteOpplysningerDbRecord> =
            forelagteOpplysningerRepository.findAllByForelagtIsNull()

        totaltAntallForelagteOpplysningerSjekk.sjekk(usendteForelagteOpplysninger)

        var antallForelagteOpplysningerSendt = 0
        var antallForelagteOpplysningerHoppetOver = 0
        for (usendtForelagtOpplysning in usendteForelagteOpplysninger) {
            val bleSendt = sendForelagteOpplysningerOppgave.sendForelagteOpplysninger(usendtForelagtOpplysning.id!!, now)
            if (bleSendt) {
                antallForelagteOpplysningerSendt++
            } else {
                antallForelagteOpplysningerHoppetOver++
            }
        }
        val resultat =
            SendForelagteOpplysningerCronjobResultat(
                antallForelagteOpplysningerSendt = antallForelagteOpplysningerSendt,
                antallForelagteOpplysningerHoppetOver = antallForelagteOpplysningerHoppetOver,
            )
        log.info(
            """
            Resultat fra ${this::class.simpleName}.
                Antall sendt: $antallForelagteOpplysningerSendt. 
                Antall hoppet over: $antallForelagteOpplysningerHoppetOver
            """.trimIndent(),
        )
        return resultat
    }
}
