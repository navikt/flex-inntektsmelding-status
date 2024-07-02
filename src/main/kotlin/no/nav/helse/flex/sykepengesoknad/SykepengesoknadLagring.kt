package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class SykepengesoknadLagring(
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    val log = logger()

    fun handterSoknad(soknad: SykepengesoknadDTO) {
        if (soknad.status === SoknadsstatusDTO.SENDT) {
            if (soknad.fom == null || soknad.tom == null) {
                return
            }

            val eksisterende = sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)

            if (eksisterende == null) {
                var sendt = soknad.sendtNav?.tilOsloZone()
                if (soknad.sendtArbeidsgiver != null && soknad.sendtArbeidsgiver!!.tilOsloZone().isBefore(sendt)) {
                    sendt = soknad.sendtArbeidsgiver!!.tilOsloZone()
                }
                if (sendt == null) {
                    sendt = LocalDateTime.now().tilOsloZone()
                }

                log.info("Lagrer sykepengesøknad ${soknad.id} sendt=$sendt")

                sykepengesoknadRepository.save(
                    Sykepengesoknad(
                        orgnummer = soknad.arbeidsgiver?.orgnummer,
                        startSyketilfelle = soknad.startSyketilfelle!!,
                        fom = soknad.fom!!,
                        tom = soknad.tom!!,
                        fnr = soknad.fnr,
                        sendt = sendt,
                        opprettetDatabase = Instant.now(),
                        sykepengesoknadUuid = soknad.id,
                        soknadstype = soknad.type.name,
                    ),
                )
            }
        }
    }
}

val osloZone = ZoneId.of("Europe/Oslo")

fun LocalDateTime.tilOsloZone(): Instant = this.atZone(osloZone).toInstant()
