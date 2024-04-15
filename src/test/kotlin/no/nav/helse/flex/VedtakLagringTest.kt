package no.nav.helse.flex

import no.nav.helse.flex.inntektsmelding.InntektsmeldingKafkaDto
import no.nav.helse.flex.inntektsmelding.Status
import no.nav.helse.flex.inntektsmelding.StatusVerdi
import no.nav.helse.flex.inntektsmelding.Vedtaksperiode
import no.nav.helse.flex.kafka.INNTEKTSMELDING_STATUS_TOPIC
import no.nav.helse.flex.organisasjon.Organisasjon
import no.nav.helse.flex.vedtak.VEDTAK_TOPIC
import no.nav.helse.flex.vedtak.VedtakFattetForEksternDto
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VedtakLagringTest : FellesTestOppsett() {
    private final val fnr = "12345678901"

    private final val flexVedtaksperiode1 = UUID.randomUUID().toString()

    private final val flexOrgNr = "123456547"
    private final val fom = LocalDate.of(2022, 6, 1)

    @Test
    @Order(0)
    fun `Vi setter opp vedtaksperiode før test av vedtakslagring`() {
        organisasjonRepository.save(
            Organisasjon(
                id = null,
                orgnummer = flexOrgNr,
                navn = "FlexOrg",
                opprettet = OffsetDateTime.now().toInstant(),
                oppdatert = OffsetDateTime.now().toInstant(),
                oppdatertAv = "test",
            ),
        )

        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.TRENGER_IKKE_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = flexOrgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = flexVedtaksperiode1,
                            fom = fom.minusDays(16),
                            tom = fom.minusDays(1),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        await().atMost(10, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.existsByEksternId(flexVedtaksperiode1)
        }

        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(flexVedtaksperiode1)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.existsByInntektsmeldingId(dbId)
        }

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!
        inntektsmelding.fnr shouldBeEqualTo fnr
        inntektsmelding.eksternId shouldBeEqualTo flexVedtaksperiode1
        inntektsmelding.orgNr shouldBeEqualTo flexOrgNr

        inntektsmelding.statusHistorikk shouldHaveSize 1
        inntektsmelding.statusHistorikk.first().status shouldBeEqualTo StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING
    }

    @Test
    @Order(1)
    fun `Skal lagre vedtaksperiode fra kafka`() {
        val sisteStatus = statusRepository.hentAlleForPerson(fnr = fnr, orgNr = flexOrgNr)
        sisteStatus.shouldHaveSize(1)
        sisteStatus.first().status shouldBeEqualTo StatusVerdi.TRENGER_IKKE_INNTEKTSMELDING

        inntektsmeldingStatusRepository.findAll().toList() shouldHaveSize 1
        kafkaProducer.send(
            ProducerRecord(
                VEDTAK_TOPIC,
                null,
                fnr,
                VedtakFattetForEksternDto(
                    fødselsnummer = fnr,
                    organisasjonsnummer = flexOrgNr,
                    fom = fom.minusDays(16),
                    tom = fom.minusDays(1),
                    utbetalingId = UUID.randomUUID().toString(),
                    vedtakFattetTidspunkt = LocalDateTime.now(),
                ).serialisertTilString(),
                listOf(RecordHeader("type", "VedtakFattet".toByteArray())),
            ),
        ).get()
        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.findAll().toList().size == 2
        }

        val sisteStatusEtterpaa = statusRepository.hentAlleForPerson(fnr = fnr, orgNr = flexOrgNr)
        sisteStatusEtterpaa.shouldHaveSize(1)
        sisteStatusEtterpaa.first().status shouldBeEqualTo StatusVerdi.VEDTAK_FATTET
    }
}
