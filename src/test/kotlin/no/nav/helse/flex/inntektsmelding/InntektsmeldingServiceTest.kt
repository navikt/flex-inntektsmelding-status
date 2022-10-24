package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.organisasjon.Organisasjon
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class InntektsmeldingServiceTest : FellesTestOppsett() {

    @Autowired
    private lateinit var inntektsmeldingService: InntektsmeldingService

    val eksternId = "vedtak-id"
    val orgnummer = "org-nr"

    private fun kafkaDto() = InntektsmeldingKafkaDto(
        id = UUID.randomUUID().toString(),
        status = Status.TRENGER_IKKE_INNTEKTSMELDING,
        sykmeldt = "10293847561",
        arbeidsgiver = orgnummer,
        vedtaksperiode = Vedtaksperiode(
            id = eksternId,
            fom = LocalDate.now().minusDays(5),
            tom = LocalDate.now(),
        ),
        tidspunkt = OffsetDateTime.now(),
    )

    @BeforeAll
    fun beforeAll() {
        organisasjonRepository.save(
            Organisasjon(
                orgnummer = orgnummer,
                navn = "org",
                opprettet = Instant.now(),
                oppdatert = Instant.now(),
                oppdatertAv = "soknad",
            )
        )
    }

    @BeforeEach
    fun setUp() {
        slettFraDatabase()
    }

    @Test
    fun `Lagrer ikke duplikat MANGLER_INNTEKTSMELDING`() {
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(finnInntektsmeldingId())!!.statusHistorikk

        statusHistorikk.shouldHaveSize(1)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.MANGLER_INNTEKTSMELDING)
    }

    @Test
    fun `Lagrer MANGLER_INNTEKTSMELDING selv om TRENGER_IKKE_INNTEKTSMELDING er siste av flere statuser`() {
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.TRENGER_IKKE_INNTEKTSMELDING))
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(finnInntektsmeldingId())!!.statusHistorikk

        statusHistorikk.shouldHaveSize(3)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.MANGLER_INNTEKTSMELDING)
    }

    @Test
    fun `Lagrer MANGLER_INNTEKTSMELDING selv om HAR_INNTEKTSMELDING er siste av flere statuser`() {
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.HAR_INNTEKTSMELDING))
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))

        val statusHistorikk =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(finnInntektsmeldingId())!!.statusHistorikk

        statusHistorikk.shouldHaveSize(3)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.MANGLER_INNTEKTSMELDING)
    }

    @Test
    fun `Feiler ved mottak av MANGLER_INNTEKTSMELDING når siste status er feil`() {
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))

        StatusVerdi.values().filter {
            it !in listOf(
                StatusVerdi.MANGLER_INNTEKTSMELDING,
                StatusVerdi.HAR_INNTEKTSMELDING,
                StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING,
            )
        }.forEach {
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = finnInntektsmeldingId(),
                    opprettet = Instant.now(),
                    status = it,
                )
            )

            assertThrows<RuntimeException> {
                inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))
            }
        }
    }

    private fun finnInntektsmeldingId(): String {
        return inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)!!.id!!
    }
}
