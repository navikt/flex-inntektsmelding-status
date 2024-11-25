package no.nav.helse.flex.forelagteopplysningerainntekt

import no.nav.helse.flex.brukervarsel.Brukervarsel
import no.nav.helse.flex.logger
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.MeldingKafkaProducer
import no.nav.helse.flex.melding.OpprettMelding
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.util.tilOsloZone
import no.nav.helse.flex.varseltekst.skapForelagteOpplysningerTekst
import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Component
class OpprettBrukervarselForForelagteOpplysninger(
    private val brukervarsel: Brukervarsel,
    private val meldingKafkaProducer: MeldingKafkaProducer,
    @Value("\${FORELAGTE_OPPLYSNINGER_BASE_URL}") private val forelagteOpplysningerBaseUrl: String,
) {
    private val log = logger()

    fun opprettVarslinger(
        varselId: String,
        melding: PGobject,
        fnr: String,
        orgNavn: String,
        startSyketilfelle: LocalDate,
        now: Instant,
    ) {
        val synligFremTil = now.tilOsloZone().plusWeeks(3).toInstant()
        val lenkeTilForelagteOpplysninger = "$forelagteOpplysningerBaseUrl/$varselId"

        brukervarsel.beskjedForelagteOpplysninger(
            fnr = fnr,
            bestillingId = varselId,
            synligFremTil = synligFremTil,
            startSyketilfelle = startSyketilfelle,
            lenke = lenkeTilForelagteOpplysninger,
        )

        meldingKafkaProducer.produserMelding(
            meldingUuid = varselId,
            meldingKafkaDto =
                MeldingKafkaDto(
                    fnr = fnr,
                    opprettMelding =
                        OpprettMelding(
                            tekst = skapForelagteOpplysningerTekst(),
                            lenke = lenkeTilForelagteOpplysninger,
                            variant = Variant.INFO,
                            lukkbar = false,
                            synligFremTil = synligFremTil,
                            // TODO: Er det et bra navn?
                            meldingType = "FORELAGTE_OPPLYSNINGER",
                            metadata =
                                forelagtOpplysningTilMetadata(
                                    melding,
                                    orgNavn,
                                ),
                        ),
                ),
        )

        log.info("Sendt forelagte opplysninger varsel med id $varselId")
    }
}
