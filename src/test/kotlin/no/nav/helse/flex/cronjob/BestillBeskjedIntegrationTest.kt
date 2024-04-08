package no.nav.helse.flex.cronjob

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.inntektsmelding.Status
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.inntektsmelding.Vedtaksperiode
import no.nav.helse.flex.kafka.INNTEKTSMELDING_STATUS_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.util.osloZone
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be in`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BestillBeskjedIntegrationTest : FellesTestOppsett() {
    private final val fnr = "12345678901"
    private final val orgNr = "123456547"
    private final val eksternId = UUID.randomUUID().toString()
    private final val fom = LocalDate.of(2022, 6, 1)
    private final val tom = LocalDate.of(2022, 6, 30)

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad`() {
        val soknad =
            SykepengesoknadDTO(
                fnr = fnr,
                id = eksternId,
                type = SoknadstypeDTO.ARBEIDSTAKERE,
                status = SoknadsstatusDTO.NY,
                fom = fom,
                tom = tom,
                arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
                arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr),
            )

        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad.serialisertTilString(),
            ),
        ).get()

        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr) != null
        }
    }

    @Test
    @Order(1)
    fun `Vi mottar MANGLER_INNTEKTSMELDING`() {
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId,
                            fom = fom.minusDays(16),
                            tom = fom.minusDays(1),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until { finnInntektsmeldingId(eksternId) != null }

        val inntektsmeldingDbRecord = finnInntektsmeldingId(eksternId)!!
        val inntektsmelding =
            statusRepository
                .hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbRecord.id!!)

        inntektsmelding!!.fnr shouldBeEqualTo fnr
        inntektsmelding.statusHistorikk shouldHaveSize 1
        inntektsmelding.statusHistorikk.last().status shouldBeEqualTo StatusVerdi.MANGLER_INNTEKTSMELDING
    }

    @Test
    @Order(2)
    fun `Vi bestiller beskjed på Ditt Nav og melding på Ditt Sykefravær`() {
        bestillBeskjedJobb.jobMedParameter(opprettetFor = OffsetDateTime.now(osloZone).toInstant())

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)

        val inntektsmeldingDbRecord = finnInntektsmeldingId(eksternId)!!

        val inntektsmelding =
            statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbRecord.id!!)!!

        inntektsmelding.statusHistorikk shouldHaveSize 3
        inntektsmelding.statusHistorikk.last().status `should be in`
            listOf(
                StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )
    }

    @Test
    @Order(3)
    fun `Vi mottar MANGLER_INNTEKTSMELDING på nytt og lagrer den ikke ned i db`() {
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId,
                            fom = fom.minusDays(16),
                            tom = fom.minusDays(1),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()

        val inntektsmeldingDbRecord = finnInntektsmeldingId(eksternId)!!

        val statusHistorikk =
            Awaitility.await().during(2, TimeUnit.SECONDS).until(
                { statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbRecord.id!!)!!.statusHistorikk },
                { historikk -> historikk.last().status != StatusVerdi.MANGLER_INNTEKTSMELDING },
            )

        statusHistorikk shouldHaveSize 3
    }

    @Test
    @Order(4)
    fun `Vi mottar TRENGER_IKKE_INNTEKTSMELDING og Donner beskjed på Ditt NAV og melding på Ditt Sykefravær`() {
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.TRENGER_IKKE_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId,
                            fom = fom.minusDays(16),
                            tom = fom.minusDays(1),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()

        varslingConsumer.ventPåRecords(1)
        meldingKafkaConsumer.ventPåRecords(1)

        val inntektsmeldingDbRecord = finnInntektsmeldingId(eksternId)!!

        val statusHistorikk =
            Awaitility.await().atMost(2, TimeUnit.SECONDS).until(
                { statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbRecord.id!!)!!.statusHistorikk },
                { historikk ->
                    historikk.last().status in
                        listOf(
                            StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
                            StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT,
                        )
                },
            )

        statusHistorikk shouldHaveSize 6
    }

    @Test
    @Order(5)
    fun `Vi mottar ny MANGLER_INNTEKTSMELDING`() {
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId,
                            fom = fom.minusDays(16),
                            tom = fom.minusDays(1),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()

        val inntektsmeldingDbRecord = finnInntektsmeldingId(eksternId)!!

        val statusHistorikk =
            Awaitility.await().atMost(2, TimeUnit.SECONDS).until(
                { statusRepository.hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbRecord.id!!)!!.statusHistorikk },
                { historikk -> historikk.last().status == StatusVerdi.MANGLER_INNTEKTSMELDING },
            )

        statusHistorikk shouldHaveSize 7
    }

    @Test
    @Order(6)
    fun `Vi bestiller ny beskjed på Ditt Nav og melding på Ditt Sykefravær siden alt er Donet`() {
        bestillBeskjedJobb.jobMedParameter(opprettetFor = OffsetDateTime.now(osloZone).toInstant())

        varslingConsumer.ventPåRecords(1).first()
        meldingKafkaConsumer.ventPåRecords(1).first()

        val inntektsmeldingDbRecord = finnInntektsmeldingId(eksternId)!!

        val inntektsmelding =
            statusRepository
                .hentInntektsmeldingMedStatusHistorikk(inntektsmeldingDbRecord.id!!)

        inntektsmelding!!.statusHistorikk.last().status `should be in`
            listOf(
                StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
            )

        inntektsmelding.statusHistorikk shouldHaveSize 9
    }

    private fun finnInntektsmeldingId(eksternId: String) = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(eksternId)
}
