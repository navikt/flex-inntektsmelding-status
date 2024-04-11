package no.nav.helse.flex.vedtak

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.vedtak.VedtakDto
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
// wtf wtf wtf
@Component
class VedtakLagring (
    private val sykepengesoknadRepository: VedtakRepository
){
    val log = logger()
    fun handterVedtak (vedtak: VedtakDto) {

    }

}
