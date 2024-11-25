package no.nav.helse.flex.forelagteopplysningerainntekt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.HarForelagtForPersonMedOrgNyligSjekk
import no.nav.helse.flex.forelagteopplysningerainntekt.sjekker.TotaltAntallForelagteOpplysningerSjekk
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.toJsonNode
import no.nav.helse.flex.util.tilOsloZone
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

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

@Component
class SendForelagteOpplysningerOppgave(
    private val forelagteOpplysningerRepository: ForelagteOpplysningerRepository,
    private val hentRelevantInfoTilForelagtOpplysning: HentRelevantInfoTilForelagtOpplysning,
    private val opprettBrukervarselForForelagteOpplysninger: OpprettBrukervarselForForelagteOpplysninger,
    private val harForelagtForPersonMedOrgNyligSjekk: HarForelagtForPersonMedOrgNyligSjekk,
) {
    private val log = logger()

    @Transactional(propagation = Propagation.REQUIRED)
    fun sendForelagteOpplysninger(
        forelagteOpplysningerId: String,
        now: Instant,
    ): Boolean {
        val forelagteOpplysninger = forelagteOpplysningerRepository.findById(forelagteOpplysningerId).getOrNull()
        if (forelagteOpplysninger == null) {
            log.error("Forelagte opplysninger finnes ikke for id: $forelagteOpplysningerId")
            return false
        }

        val relevantInfoTilForelagteOpplysninger =
            hentRelevantInfoTilForelagtOpplysning.hentRelevantInfoFor(
                vedtaksperiodeId = forelagteOpplysninger.vedtaksperiodeId,
                behandlingId = forelagteOpplysninger.behandlingId,
            )
        if (relevantInfoTilForelagteOpplysninger == null) {
            log.warn("Kunne ikke hente relevant info til forelagte opplysninger: ${forelagteOpplysninger.id}")
            return false
        }

        if (!harForelagtForPersonMedOrgNyligSjekk.sjekk(
                fnr = relevantInfoTilForelagteOpplysninger.fnr,
                orgnummer = relevantInfoTilForelagteOpplysninger.orgnummer,
                now = now,
            )
        ) {
            log.warn(
                "Har forelagt nylig for person med org, forelgger ikke på nytt nå. Forelagte opplysninger: ${forelagteOpplysninger.id}",
            )
            return false
        }

        opprettBrukervarselForForelagteOpplysninger.opprettVarslinger(
            varselId = forelagteOpplysninger.id!!,
            melding = forelagteOpplysninger.forelagteOpplysningerMelding,
            fnr = relevantInfoTilForelagteOpplysninger.fnr,
            orgNavn = relevantInfoTilForelagteOpplysninger.orgnummer,
            now = now,
            startSyketilfelle = relevantInfoTilForelagteOpplysninger.startSyketilfelle,
        )
        forelagteOpplysningerRepository.save(
            forelagteOpplysninger.copy(forelagt = now),
        )
        return true
    }
}

internal fun forelagtOpplysningTilMetadata(
    forelagtOpplysningMelding: PGobject,
    orgNavn: String,
): JsonNode {
    val serialisertMelding = forelagtOpplysningMelding.value ?: error("Melding må ha value")
    val deserialisertMelding: ForelagteOpplysningerMelding =
        objectMapper.readValue(serialisertMelding)
    val aaregInntekt =
        AaregInntekt(
            tidsstempel = deserialisertMelding.tidsstempel,
            inntekter = deserialisertMelding.skatteinntekter.map { AaregInntekt.Inntekt(it.måned.toString(), it.beløp) },
            omregnetAarsinntekt = deserialisertMelding.omregnetÅrsinntekt,
            orgnavn = orgNavn,
        )
    return aaregInntekt.toJsonNode()
}
