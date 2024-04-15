package no.nav.helse.flex.inntektsmelding

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.organisasjon.Organisasjon
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class InntektsmeldingServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var inntektsmeldingService: InntektsmeldingService

    val eksternId = "vedtak-id"
    val orgnummer = "org-nr"

    private fun kafkaDto() =
        InntektsmeldingKafkaDto(
            id = UUID.randomUUID().toString(),
            status = Status.TRENGER_IKKE_INNTEKTSMELDING,
            sykmeldt = "10293847561",
            arbeidsgiver = orgnummer,
            vedtaksperiode =
                Vedtaksperiode(
                    id = eksternId,
                    fom = LocalDate.now().minusDays(5),
                    tom = LocalDate.now(),
                ),
            tidspunkt = OffsetDateTime.now(),
        )

    @BeforeEach
    fun setUp() {
        slettFraDatabase()
                organisasjonRepository.save(
            Organisasjon(
                orgnummer = orgnummer,
                navn = "org",
                opprettet = Instant.now(),
                oppdatert = Instant.now(),
                oppdatertAv = "soknad",
            ),
        )

    }

    @Test
    fun `Lagrer ikke duplikat MANGLER_INNTEKTSMELDING`() {
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.HAR_INNTEKTSMELDING))

        val statusHistorikk =
            Awaitility.await().atMost(2, TimeUnit.SECONDS).until(
                { statusRepository.hentInntektsmeldingMedStatusHistorikk(finnInntektsmeldingId())!!.statusHistorikk },
                { it.last().status == StatusVerdi.HAR_INNTEKTSMELDING },
            )

        statusHistorikk.shouldHaveSize(2)
        statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.HAR_INNTEKTSMELDING)
    }

    @Test
    fun `Lagrer MANGLER_INNTEKTSMELDING selv om en annen status allerede eksisterer`() {
        inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))

        await().atMost(2, TimeUnit.SECONDS).until {
            statusRepository.hentInntektsmeldingMedStatusHistorikk(finnInntektsmeldingId())!!.statusHistorikk.size == 1
        }

        var antallMeldinger = 1

        StatusVerdi.values().filter {
            it !in
                listOf(
                    StatusVerdi.MANGLER_INNTEKTSMELDING,
                    StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
                    StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                )
        }.forEach {
            inntektsmeldingStatusRepository.save(
                InntektsmeldingStatusDbRecord(
                    inntektsmeldingId = finnInntektsmeldingId(),
                    opprettet = Instant.now(),
                    status = it,
                ),
            )

            inntektsmeldingService.prosesserKafkaMelding(kafkaDto().copy(status = Status.MANGLER_INNTEKTSMELDING))

            val statusHistorikk =
                Awaitility.await().atMost(2, TimeUnit.SECONDS).until(
                    { statusRepository.hentInntektsmeldingMedStatusHistorikk(finnInntektsmeldingId())!!.statusHistorikk },
                    { historikk -> historikk.last().status == StatusVerdi.MANGLER_INNTEKTSMELDING },
                )

            // Teller opp aktuelle StatusVerdi + påfølgende MANGLER_INNTEKTSMELDING.
            antallMeldinger += 2
            statusHistorikk.shouldHaveSize(antallMeldinger)
            statusHistorikk.last().status.shouldBeEqualTo(StatusVerdi.MANGLER_INNTEKTSMELDING)
        }
    }

    private fun finnInntektsmeldingId(): String {
        return inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)!!.id!!
    }
}
