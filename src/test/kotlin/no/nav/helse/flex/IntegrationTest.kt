package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.inntektsmelding.Status
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.inntektsmelding.Vedtaksperiode
import no.nav.helse.flex.kafka.bomloInntektsmeldingManglerTopic
import no.nav.helse.flex.kafka.sykepengesoknadTopic
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.osloZone
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
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
    private final val orgNr = "123456547"
    private final val fom = LocalDate.of(2022, 6, 1)
    private final val tom = LocalDate.of(2022, 6, 30)

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        val soknad = SykepengesoknadDTO(
            fnr = fnr,
            id = eksternId,
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = SoknadsstatusDTO.NY,
            fom = fom,
            tom = tom,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr)
        )

        kafkaProducer.send(
            ProducerRecord(
                sykepengesoknadTopic,
                soknad.id,
                soknad.serialisertTilString()
            )
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at det mangler en inntektsmelding`() {
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
        ).get()

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
        inntektsmelding.statusHistorikk.first() shouldBeEqualTo StatusVerdi.MANGLER_INNTEKTSMELDING
    }

    @Test
    @Order(2)
    fun `Vi bestiller beskjed på ditt nav og melding på ditt sykefravær`() {
        bestillBeskjed.jobMedParameter(opprettetFor = OffsetDateTime.now(osloZone).toInstant())

        val beskjedCR = beskjedKafkaConsumer.ventPåRecords(1).first()

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
        beskjedInput.get("tekst") shouldBeEqualTo "Vi mangler inntektsmeldingen fra Flex AS for sykefravær f.o.m. 1. juni 2022."
        beskjedInput.get("tidspunkt")

        val synligFremTil = Instant.ofEpochMilli(beskjedInput.get("synligFremTil") as Long)
        synligFremTil.shouldBeAfter(OffsetDateTime.now().plusMinutes(19).toInstant())
        synligFremTil.shouldBeBefore(OffsetDateTime.now().plusMinutes(21).toInstant())

        val meldingCR = meldingKafkaConsumer.ventPåRecords(1).first()
        meldingCR.key() shouldBeEqualTo eksternId

        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo "Vi mangler inntektsmeldingen fra Flex AS for sykefravær f.o.m. 1. juni 2022."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.info
        opprettMelding.synligFremTil.shouldNotBeNull()

        opprettMelding.synligFremTil!!.shouldBeAfter(OffsetDateTime.now().plusMinutes(19).toInstant())
        opprettMelding.synligFremTil!!.shouldBeBefore(OffsetDateTime.now().plusMinutes(21).toInstant())
    }

    @Test
    @Order(3)
    fun `Vi mottar inntektsmeldingen`() {
        kafkaProducer.send(
            ProducerRecord(
                bomloInntektsmeldingManglerTopic,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.HAR_INNTEKTSMELDING,
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
        ).get()

        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)?.statusHistorikk?.contains(StatusVerdi.HAR_INNTEKTSMELDING)
        }
    }

    @Test
    @Order(4)
    fun `Beskjed og melding donnes`() {
        val doneBrukernotifikasjon = doneKafkaConsumer
            .ventPåRecords(1)
            .first()
            .key()

        doneBrukernotifikasjon.get("grupperingsId") shouldBeEqualTo eksternId

        val doneDittSykefravaer: MeldingKafkaDto = meldingKafkaConsumer
            .ventPåRecords(1)
            .first()
            .value()
            .let { objectMapper.readValue(it) }

        doneDittSykefravaer.lukkMelding.shouldNotBeNull()
    }

    @Test
    @Order(5)
    fun `Vi har bestilt nytt ditt nav beskjed og ditt sykefravær melding`() {
        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)!!.id!!
        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!

        inntektsmelding.statusHistorikk shouldBeEqualTo listOf(
            StatusVerdi.MANGLER_INNTEKTSMELDING,

            StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
            StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,

            StatusVerdi.HAR_INNTEKTSMELDING,

            StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
            StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT,

            // TODO: Bestill mottatt inntektsmelding melding på ditt sykefravær
            // StatusVerdi.DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT,
        )
    }
}
