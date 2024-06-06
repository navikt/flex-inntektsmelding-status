package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.serialisertTilString
import no.nav.inntektsmeldingkontrakt.Arbeidsgivertype
import no.nav.inntektsmeldingkontrakt.AvsenderSystem
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import no.nav.inntektsmeldingkontrakt.Periode
import no.nav.inntektsmeldingkontrakt.Refusjon
import no.nav.inntektsmeldingkontrakt.Status
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.*
import java.util.concurrent.TimeUnit

class InntektsmeldingTest : FellesTestOppsett() {
    @Autowired
    lateinit var inntektsmeldingRepository: InntektsmeldingRepository

    @Autowired
    lateinit var producer: KafkaProducer<String, String>

    @BeforeEach
    fun clearDatabase() {
        inntektsmeldingRepository.deleteAll()
    }

    @Test
    fun `Mottar inntektsmeldingmelding med full refusjon`() {
        val fnr = "12345678787"

        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(0)
        produserMelding(
            UUID.randomUUID().toString(),
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = "123456789",
                refusjonBelopPerMnd = BigDecimal(10000),
                beregnetInntekt = BigDecimal(10000),
            ).serialisertTilString(),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(1)

        val melding = inntektsmeldingRepository.findByFnrIn(listOf(fnr)).first()
        melding.arbeidsgivertype `should be equal to` "VIRKSOMHET"
        melding.fullRefusjon `should be equal to` true
        melding.vedtaksperiodeId `should be equal to` "ffcf0c28-dd35-4b5d-b518-3d3cbda9329a"
    }

    @Test
    fun `Mottar inntektsmeldingmelding med delvis refusjon`() {
        val fnr = "54545454"

        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(0)
        produserMelding(
            UUID.randomUUID().toString(),
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = "123456789",
                refusjonBelopPerMnd = BigDecimal(5000),
                beregnetInntekt = BigDecimal(10000),
            ).serialisertTilString(),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(1)

        val melding = inntektsmeldingRepository.findByFnrIn(listOf(fnr)).first()
        melding.arbeidsgivertype `should be equal to` "VIRKSOMHET"
        melding.fullRefusjon `should be equal to` false
    }

    @Test
    fun `Mottar inntektsmeldingmelding med null refusjon`() {
        val fnr = "34533452"

        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(0)
        produserMelding(
            UUID.randomUUID().toString(),
            skapInntektsmelding(
                fnr = fnr,
                virksomhetsnummer = "123456789",
                refusjonBelopPerMnd = null,
                beregnetInntekt = BigDecimal(10000),
            ).serialisertTilString(),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.findByFnrIn(listOf(fnr)).isNotEmpty()
        }
        inntektsmeldingRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(1)

        val melding = inntektsmeldingRepository.findByFnrIn(listOf(fnr)).first()
        melding.arbeidsgivertype `should be equal to` "VIRKSOMHET"
        melding.fullRefusjon `should be equal to` false
    }

    fun produserMelding(
        meldingUuid: String,
        melding: String,
    ): RecordMetadata {
        return producer.send(
            ProducerRecord(
                INNTEKTSMELDING_TOPIC,
                meldingUuid,
                melding,
            ),
        ).get()
    }
}

fun skapInntektsmelding(
    fnr: String,
    virksomhetsnummer: String?,
    refusjonBelopPerMnd: BigDecimal?,
    beregnetInntekt: BigDecimal?,
): Inntektsmelding {
    val foersteJanuar = LocalDate.of(2019, 1, 1)
    val andreJanuar = LocalDate.of(2019, 1, 2)
    return Inntektsmelding(
        inntektsmeldingId = UUID.randomUUID().toString(),
        arbeidstakerFnr = fnr,
        arbeidstakerAktorId = "00000000000",
        refusjon = Refusjon(beloepPrMnd = refusjonBelopPerMnd),
        endringIRefusjoner = emptyList(),
        opphoerAvNaturalytelser = emptyList(),
        gjenopptakelseNaturalytelser = emptyList(),
        status = Status.GYLDIG,
        arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
        arbeidsgiverperioder = listOf(Periode(foersteJanuar, andreJanuar)),
        beregnetInntekt = beregnetInntekt,
        inntektsdato = LocalDate.of(2023, Month.OCTOBER, 13),
        arkivreferanse = "AR123",
        ferieperioder = emptyList(),
        mottattDato = foersteJanuar.atStartOfDay(),
        foersteFravaersdag = foersteJanuar,
        naerRelasjon = true,
        avsenderSystem = AvsenderSystem("AltinnPortal", "1.0"),
        innsenderFulltNavn = "",
        innsenderTelefon = "",
        virksomhetsnummer = virksomhetsnummer,
        vedtaksperiodeId = UUID.fromString("ffcf0c28-dd35-4b5d-b518-3d3cbda9329a"),
    )
}
