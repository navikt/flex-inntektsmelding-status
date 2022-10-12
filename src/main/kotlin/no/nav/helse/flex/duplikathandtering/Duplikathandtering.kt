package no.nav.helse.flex.duplikathandtering

import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.inntektsmelding.InntektsmeldingMedStatus
import no.nav.helse.flex.inntektsmelding.StatusRepository
import no.nav.helse.flex.inntektsmelding.tilStatusVerdi
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class Duplikathandtering(

    private val statusRepository: StatusRepository,

) {
    val log = logger()

    @Transactional
    fun prosesserKafkaMelding(kafkaDto: InntektsmeldingKafkaDto) {
        val alleForPerson = statusRepository.hentAlleForPerson(fnr = kafkaDto.sykmeldt, orgNr = kafkaDto.arbeidsgiver)

        fun InntektsmeldingMedStatus.erLikMedFeilEksternId(): Boolean {
            val erEldre = eksternTimestamp.isBefore(kafkaDto.tidspunkt.toInstant())
            val harSammeStatus = this.status == kafkaDto.status.tilStatusVerdi()
            val harSammePeriode =
                this.vedtakFom == kafkaDto.vedtaksperiode.fom && this.vedtakTom == kafkaDto.vedtaksperiode.tom
            val harForskjelligEksternId = kafkaDto.vedtaksperiode.id != this.eksternId

            return erEldre && harSammeStatus && harSammePeriode && harForskjelligEksternId
        }

        alleForPerson
            .filter { it.erLikMedFeilEksternId() }
            .forEach {
                log.info("Eldre funksjonelt duplikat med ekstern id ${it.eksternId} for nyere ${kafkaDto.vedtaksperiode.id}")
            }
    }
}
