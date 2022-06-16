package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.inntektsmelding.Status
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.inntektsmelding.Vedtaksperiode
import no.nav.helse.flex.kafka.bomloInntektsmeldingManglerTopic
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest : FellesTestOppsett() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    private final val fnr = "12345678901"
    private final val eksternId = UUID.randomUUID().toString()

    @Test
    @Order(1)
    fun `Vi får beskjed at det mangler en inntektsmelding`() {
        val orgNr = "org"
        val fom = LocalDate.of(2022, 6, 1)
        val tom = LocalDate.of(2022, 6, 30)

        kafkaProducer.send(
            ProducerRecord(
                bomloInntektsmeldingManglerTopic,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode = Vedtaksperiode(
                        id = eksternId,
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

    @Test
    @Order(2)
    fun `Vi bestiller beskjed på ditt nav og melding på ditt sykefravær`() {
        bestillBeskjed.jobMedParameter(opprettetFor = Instant.now())

        await().atMost(5, TimeUnit.SECONDS).until {
            statusRepository.hentAlleMedNyesteStatus(
                StatusVerdi.BRUKERNOTIFIKSJON_SENDT,
                StatusVerdi.DITT_SYKEFRAVAER_MELDING_SENDT
            ).size == 2
        }

        val beskjedCR = beskjedKafkaConsumer.ventPåRecords(1)[0]

        val nokkelInput = beskjedCR.key()
        nokkelInput.get("appnavn") shouldBeEqualTo "flex-inntektsmelding-status"
        nokkelInput.get("namespace") shouldBeEqualTo "flex"
        nokkelInput.get("fodselsnummer") shouldBeEqualTo fnr
        nokkelInput.get("eventId") shouldBeEqualTo eksternId
        nokkelInput.get("grupperingsId") shouldBeEqualTo eksternId

        val beskjedInput = beskjedCR.value()
        beskjedInput.get("eksternVarsling") shouldBeEqualTo false
        beskjedInput.get("link") shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.get("sikkerhetsnivaa") shouldBeEqualTo 4
        beskjedInput.get("tekst") shouldBeEqualTo "Vi mangler inntektsmeldingen fra  for sykefravær f.o.m. 1. juni 2022. Se mer informasjon."
        beskjedInput.get("tidspunkt")

        val meldingCR = meldingKafkaConsumer.ventPåRecords(1)[0]
        meldingCR.key() shouldBeEqualTo eksternId

        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo "Vi mangler inntektsmeldingen fra  for sykefravær f.o.m. 1. juni 2022."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.info
        opprettMelding.synligFremTil.shouldBeNull()
    }
}
