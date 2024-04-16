package no.nav.helse.flex

import no.nav.helse.flex.kafka.INNTEKTSMELDING_STATUS_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.osloZone
import no.nav.helse.flex.vedtaksperiode.InntektsmeldingKafkaDto
import no.nav.helse.flex.vedtaksperiode.Status
import no.nav.helse.flex.vedtaksperiode.StatusVerdi
import no.nav.helse.flex.vedtaksperiode.Vedtaksperiode
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FlereArbeidsgivereTest : FellesTestOppsett() {
    private final val fnr = "12345678901"

    private final val bomloSykepengesoknadId = UUID.randomUUID().toString()

    private final val bomloVedtaksperiode1 = UUID.randomUUID().toString()
    private final val bomloVedtaksperiode2 = UUID.randomUUID().toString()
    private final val bomloVedtaksperiode3 = UUID.randomUUID().toString()

    private final val flexSykepengesoknadId = UUID.randomUUID().toString()

    private final val flexVedtaksperiode1 = UUID.randomUUID().toString()
    private final val flexVedtaksperiode2 = UUID.randomUUID().toString()
    private final val flexVedtaksperiode3 = UUID.randomUUID().toString()

    private final val flexOrgNr = "123456547"
    private final val bomloOrgNr = "923456547"
    private final val fom = LocalDate.of(2022, 6, 1)
    private final val tom = LocalDate.of(2022, 6, 30)

    val manglerBeskjedBestillingId: String by lazy {
        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(flexSykepengesoknadId)!!.id!!
        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!
        inntektsmelding
            .statusHistorikk
            .first { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }
            .id
    }
    val manglerMeldingBestillingId: String by lazy {
        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(flexSykepengesoknadId)!!.id!!
        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!
        inntektsmelding
            .statusHistorikk
            .first { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }
            .id
    }

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
        val soknad =
            SykepengesoknadDTO(
                fnr = fnr,
                id = flexSykepengesoknadId,
                type = SoknadstypeDTO.ARBEIDSTAKERE,
                status = SoknadsstatusDTO.NY,
                fom = fom,
                tom = tom,
                arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
                arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = flexOrgNr),
            )

        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad.serialisertTilString(),
            ),
        ).get()

        val soknad2 =
            SykepengesoknadDTO(
                fnr = fnr,
                id = bomloSykepengesoknadId,
                type = SoknadstypeDTO.ARBEIDSTAKERE,
                status = SoknadsstatusDTO.NY,
                fom = fom,
                tom = tom,
                arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
                arbeidsgiver = ArbeidsgiverDTO(navn = "Bømlo AS", orgnummer = bomloOrgNr),
            )

        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad.id,
                soknad.serialisertTilString(),
            ),
        ).get()
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(flexOrgNr)?.navn == "Flex AS"
        }

        kafkaProducer.send(
            ProducerRecord(
                SYKEPENGESOKNAD_TOPIC,
                soknad2.id,
                soknad2.serialisertTilString(),
            ),
        ).get()
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(bomloOrgNr)?.navn == "Bømlo AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at det mangler en inntektsmelding for tre perioder butt i butt for ag 1`() {
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
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = flexOrgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = flexVedtaksperiode2,
                            fom = fom,
                            tom = tom,
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = flexOrgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = flexVedtaksperiode3,
                            fom = tom.plusDays(1),
                            tom = tom.plusDays(5),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.existsByEksternId(flexVedtaksperiode1) &&
                inntektsmeldingRepository.existsByEksternId(
                    flexVedtaksperiode2,
                ) &&
                inntektsmeldingRepository.existsByEksternId(
                    flexVedtaksperiode3,
                )
        }

        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(flexVedtaksperiode2)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.existsByInntektsmeldingId(dbId)
        }

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!
        inntektsmelding.fnr shouldBeEqualTo fnr
        inntektsmelding.eksternId shouldBeEqualTo flexVedtaksperiode2
        inntektsmelding.orgNr shouldBeEqualTo flexOrgNr
        inntektsmelding.vedtakFom shouldBeEqualTo fom
        inntektsmelding.vedtakTom shouldBeEqualTo tom

        inntektsmelding.statusHistorikk shouldHaveSize 1
        inntektsmelding.statusHistorikk.first().status shouldBeEqualTo StatusVerdi.MANGLER_INNTEKTSMELDING
    }

    @Test
    @Order(2)
    fun `Vi får beskjed at det mangler en inntektsmelding for tre perioder butt i butt for ag 2`() {
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.TRENGER_IKKE_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = bomloOrgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = bomloVedtaksperiode1,
                            fom = fom.minusDays(16),
                            tom = fom.minusDays(1),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = bomloOrgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = bomloVedtaksperiode2,
                            fom = fom,
                            tom = tom,
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.MANGLER_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = bomloOrgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = bomloVedtaksperiode3,
                            fom = tom.plusDays(1),
                            tom = tom.plusDays(5),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingRepository.existsByEksternId(bomloVedtaksperiode1) &&
                inntektsmeldingRepository.existsByEksternId(bomloVedtaksperiode2) &&
                inntektsmeldingRepository.existsByEksternId(
                    bomloVedtaksperiode3,
                )
        }

        val dbId = inntektsmeldingRepository.findInntektsmeldingDbRecordByEksternId(bomloVedtaksperiode2)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            inntektsmeldingStatusRepository.existsByInntektsmeldingId(dbId)
        }

        val inntektsmelding = statusRepository.hentInntektsmeldingMedStatusHistorikk(dbId)!!
        inntektsmelding.fnr shouldBeEqualTo fnr
        inntektsmelding.eksternId shouldBeEqualTo bomloVedtaksperiode2
        inntektsmelding.orgNr shouldBeEqualTo bomloOrgNr
        inntektsmelding.vedtakFom shouldBeEqualTo fom
        inntektsmelding.vedtakTom shouldBeEqualTo tom

        inntektsmelding.statusHistorikk shouldHaveSize 1
        inntektsmelding.statusHistorikk.first().status shouldBeEqualTo StatusVerdi.MANGLER_INNTEKTSMELDING
    }

    @Test
    @Order(3)
    fun `Vi bestiller 2 beskjeder på Ditt NAV og 2 meldinger på Ditt Sykefravær`() {
        bestillBeskjedJobb.jobMedParameter(opprettetFor = OffsetDateTime.now(osloZone).toInstant())

        varslingConsumer.ventPåRecords(2)
        meldingKafkaConsumer.ventPåRecords(2)
    }
}
