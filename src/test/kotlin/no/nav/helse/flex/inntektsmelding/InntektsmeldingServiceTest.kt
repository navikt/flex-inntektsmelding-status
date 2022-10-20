package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.organisasjon.Organisasjon
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class InntektsmeldingServiceTest : FellesTestOppsett() {

    @Autowired
    private lateinit var inntektsmeldingService: InntektsmeldingService

    private fun kafkaDto() = InntektsmeldingKafkaDto(
        id = UUID.randomUUID().toString(),
        status = Status.TRENGER_IKKE_INNTEKTSMELDING,
        sykmeldt = "10293847561",
        arbeidsgiver = org,
        vedtaksperiode = Vedtaksperiode(
            id = eksternId,
            fom = LocalDate.now().minusDays(5),
            tom = LocalDate.now(),
        ),
        tidspunkt = OffsetDateTime.now(),
    )

    val eksternId = "vedtak-id"
    val org = "56473829"
    val inntektsmeldingDbId: String by lazy {
        inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)!!.id!!
    }

    @BeforeAll
    fun beforeAll() {
        organisasjonRepository.save(
            Organisasjon(
                orgnummer = org,
                navn = "org",
                opprettet = Instant.now(),
                oppdatert = Instant.now(),
                oppdatertAv = "soknad",
            )
        )
    }

    @Test
    @Order(1)
    fun `Mottar først TRENGER_IKKE_INNTEKTSMELDING`() {
        inntektsmeldingService.prosesserKafkaMelding(
            kafkaDto().copy(status = Status.TRENGER_IKKE_INNTEKTSMELDING)
        )

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbId)!!.statusHistorikk

        statusHistorikk.shouldHaveSize(1)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING)
    }

    @Test
    @Order(2)
    fun `Mottar deretter MANGLER_INNTEKTSMELDING`() {
        inntektsmeldingService.prosesserKafkaMelding(
            kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING)
        )

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbId)!!.statusHistorikk

        statusHistorikk.shouldHaveSize(2)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.MANGLER_INNTEKTSMELDING)

        statusRepository.hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .first()
            .id.shouldBeEqualTo(inntektsmeldingDbId)
    }

    @Test
    @Order(3)
    fun `Mottar MANGLER_INNTEKTSMELDING en gang til men lagrer ikke duplikat`() {
        inntektsmeldingService.prosesserKafkaMelding(
            kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING)
        )

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbId)!!.statusHistorikk

        statusHistorikk.shouldHaveSize(2)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.MANGLER_INNTEKTSMELDING)

        statusRepository.hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .first()
            .id.shouldBeEqualTo(inntektsmeldingDbId)
    }

    @Test
    @Order(4)
    fun `Mottar TRENGER_IKKE_INNTEKTSMELDING en gang til og lagrer ny status`() {
        inntektsmeldingService.prosesserKafkaMelding(
            kafkaDto().copy(status = Status.TRENGER_IKKE_INNTEKTSMELDING)
        )

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbId)!!.statusHistorikk

        statusHistorikk.shouldHaveSize(3)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING)

        statusRepository.hentAlleMedNyesteStatus(StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING)
            .first()
            .id.shouldBeEqualTo(inntektsmeldingDbId)
    }

    @Test
    @Order(5)
    fun `Mottar MANGLER_INNTEKTSMELDING en gang til og lagrer ny status`() {
        inntektsmeldingService.prosesserKafkaMelding(
            kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING)
        )

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbId)!!.statusHistorikk

        statusHistorikk.shouldHaveSize(4)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.MANGLER_INNTEKTSMELDING)

        statusRepository.hentAlleMedNyesteStatus(StatusVerdi.MANGLER_INNTEKTSMELDING)
            .first()
            .id.shouldBeEqualTo(inntektsmeldingDbId)
    }
}
