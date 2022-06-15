package no.nav.helse.flex

import no.nav.helse.flex.inntektsmelding.Hendelse
import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.kafka.bomloInntektsmeldingManglerTopic
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest : FellesTestOppsett() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    private final val fnr = "12345678901"

    @Test
    @Order(1)
    fun `Vi får beskjed at det mangler en inntektsmelding`() {
        val eksternId = UUID.randomUUID()
        val orgNr = "org"
        val fom = LocalDate.now().minusWeeks(3)
        val tom = LocalDate.now().minusWeeks(2)

        kafkaProducer.send(
            ProducerRecord(
                bomloInntektsmeldingManglerTopic,
                fnr,
                InntektsmeldingKafkaDto(
                    uuid = eksternId,
                    fnr = fnr.toLong(),
                    orgnummer = orgNr,
                    vedtaksperiodeFom = fom,
                    vedtaksperiodeTom = tom,
                    hendelse = Hendelse.INNTEKTSMELDING_MANGLER,
                ).serialisertTilString()
            )
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.existsByEksternId(eksternId.toString())
        }

        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId.toString())!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.existsByInntektsmeldingId(dbId)
        }

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!
        inntektsmelding.fnr shouldBeEqualTo fnr
        inntektsmelding.eksternId shouldBeEqualTo eksternId.toString()
        inntektsmelding.orgNr shouldBeEqualTo orgNr
        inntektsmelding.vedtakFom shouldBeEqualTo fom
        inntektsmelding.vedtakTom shouldBeEqualTo tom

        inntektsmelding.statusHistorikk shouldHaveSize 1
        inntektsmelding.statusHistorikk.first().status shouldBeEqualTo StatusVerdi.MANGLER
    }
}
