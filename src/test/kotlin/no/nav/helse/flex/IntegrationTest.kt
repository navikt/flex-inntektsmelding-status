package no.nav.helse.flex

import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.inntektsmelding.Status
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.inntektsmelding.Vedtaksperiode
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
import java.time.OffsetDateTime
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
        val eksternId = UUID.randomUUID().toString()
        val orgNr = "org"
        val fom = LocalDate.now().minusWeeks(3)
        val tom = LocalDate.now().minusWeeks(2)

        kafkaProducer.send(
            ProducerRecord(
                bomloInntektsmeldingManglerTopic,
                fnr,
                InntektsmeldingKafkaDto(
                    id = eksternId,
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode = Vedtaksperiode(
                        id = UUID.randomUUID().toString(),
                        fom = fom,
                        tom = tom,
                    ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString()
            )
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.existsByEksternId(eksternId)
        }

        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.existsByInntektsmeldingId(dbId)
        }

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!
        inntektsmelding.fnr shouldBeEqualTo fnr
        inntektsmelding.eksternId shouldBeEqualTo eksternId
        inntektsmelding.orgNr shouldBeEqualTo orgNr
        inntektsmelding.vedtakFom shouldBeEqualTo fom
        inntektsmelding.vedtakTom shouldBeEqualTo tom

        inntektsmelding.statusHistorikk shouldHaveSize 1
        inntektsmelding.statusHistorikk.first().status shouldBeEqualTo StatusVerdi.MANGLER_INNTEKTSMELDING
    }
}
