package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.INNTEKTSMELDING_STATUS_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.melding.Variant
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.util.osloZone
import no.nav.helse.flex.vedtaksperiode.InntektsmeldingKafkaDto
import no.nav.helse.flex.vedtaksperiode.Status
import no.nav.helse.flex.vedtaksperiode.StatusVerdi
import no.nav.helse.flex.vedtaksperiode.Vedtaksperiode
import no.nav.tms.varsel.action.Sensitivitet
import org.amshove.kluent.*
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
class IntegrationTest : FellesTestOppsett() {
    private final val fnr = "12345678901"
    private final val eksternIdAG = UUID.randomUUID().toString()
    private final val eksternId = UUID.randomUUID().toString()
    private final val eksternId2 = UUID.randomUUID().toString()
    private final val orgNr = "123456547"
    private final val fom = LocalDate.of(2022, 6, 1)
    private final val tom = LocalDate.of(2022, 6, 30)

    val manglerBeskjedBestillingId: String by lazy {
        val dbId = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId)!!.id!!
        val inntektsmelding = statusRepository.hentVedtaksperiodeMedStatusHistorikk(dbId)!!
        inntektsmelding
            .statusHistorikk
            .first { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }
            .id
    }
    val manglerMeldingBestillingId: String by lazy {
        val dbId = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId)!!.id!!
        val inntektsmelding = statusRepository.hentVedtaksperiodeMedStatusHistorikk(dbId)!!
        inntektsmelding
            .statusHistorikk
            .first { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }
            .id
    }
    val mottatMeldingBestillingId: String by lazy {
        val dbId = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId)!!.id!!
        val inntektsmelding = statusRepository.hentVedtaksperiodeMedStatusHistorikk(dbId)!!
        inntektsmelding
            .statusHistorikk
            .first { it.status == StatusVerdi.DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT }
            .id
    }

    @Test
    @Order(0)
    fun `Sykmeldt sender inn sykepengesøknad, vi henter ut arbeidsgivers navn`() {
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

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            organisasjonRepository.findByOrgnummer(orgNr)?.navn == "Flex AS"
        }
    }

    @Test
    @Order(1)
    fun `Vi får beskjed at det mangler en inntektsmelding for tre perioder butt i butt, første periode er innenfor AG`() {
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
                            id = eksternIdAG,
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
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId,
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
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId2,
                            fom = tom.plusDays(1),
                            tom = tom.plusDays(5),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        await().atMost(5, TimeUnit.SECONDS).until {
            vedtaksperiodeRepository.existsByEksternId(eksternId) &&
                vedtaksperiodeRepository.existsByEksternId(
                    eksternId2,
                )
        }

        val dbId = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            vedtaksperiodeStatusRepository.existsByVedtaksperiodeDbId(dbId)
        }

        val inntektsmelding = statusRepository.hentVedtaksperiodeMedStatusHistorikk(dbId)!!
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
    fun `Vi bestiller beskjed på Ditt NAV og melding på Ditt Sykefravær`() {
        bestillBeskjedJobb.jobMedParameter(opprettetFor = OffsetDateTime.now(osloZone).toInstant())

        val beskjedCR = varslingConsumer.ventPåRecords(1).first()

        val nokkelInput = beskjedCR.key()
        nokkelInput shouldBeEqualTo manglerBeskjedBestillingId

        val beskjedInput = beskjedCR.value().tilOpprettVarselInstance()
        beskjedInput.ident shouldBeEqualTo fnr
        beskjedInput.varselId shouldBeEqualTo manglerBeskjedBestillingId
        beskjedInput.eksternVarsling.shouldNotBeNull()
        beskjedInput.link shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        beskjedInput.sensitivitet shouldBeEqualTo Sensitivitet.High
        beskjedInput.tekster.first().tekst shouldBeEqualTo
            "Du har gjort din del. Nå venter vi på inntektsmeldingen fra Flex AS for sykefraværet som startet 16. mai 2022."

        val synligFremTil = beskjedInput.aktivFremTil!!.toInstant()
        synligFremTil.shouldBeAfter(OffsetDateTime.now().plusMinutes(19).toInstant())
        synligFremTil.shouldBeBefore(OffsetDateTime.now().plusMinutes(21).toInstant())

        val meldingCR = meldingKafkaConsumer.ventPåRecords(1).first()
        meldingCR.key() shouldBeEqualTo manglerMeldingBestillingId

        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MANGLENDE_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo
            "Du har gjort din del. Nå venter vi på inntektsmeldingen fra Flex AS for sykefraværet som startet 16. mai 2022."
        opprettMelding.lenke shouldBeEqualTo "https://www-gcp.dev.nav.no/syk/sykefravaer/inntektsmelding"
        opprettMelding.lukkbar shouldBeEqualTo false
        opprettMelding.variant shouldBeEqualTo Variant.INFO
        opprettMelding.synligFremTil.shouldNotBeNull()

        opprettMelding.synligFremTil!!.shouldBeAfter(OffsetDateTime.now().plusMinutes(19).toInstant())
        opprettMelding.synligFremTil!!.shouldBeBefore(OffsetDateTime.now().plusMinutes(21).toInstant())
    }

    @Test
    @Order(3)
    fun `Vi mottar inntektsmeldingen`() {
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.HAR_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId,
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
                    status = Status.HAR_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId2,
                            fom = tom.plusDays(1),
                            tom = tom.plusDays(5),
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()
        val dbId = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            statusRepository.hentVedtaksperiodeMedStatusHistorikk(
                dbId,
            )?.statusHistorikk?.any { it.status == StatusVerdi.HAR_INNTEKTSMELDING }
        }
        val dbId2 = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId2)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            statusRepository.hentVedtaksperiodeMedStatusHistorikk(
                dbId2,
            )?.statusHistorikk?.any { it.status == StatusVerdi.HAR_INNTEKTSMELDING }
        }
    }

    @Test
    @Order(4)
    fun `Beskjed og melding donnes`() {
        val doneBrukernotifikasjon =
            varslingConsumer
                .ventPåRecords(1)
                .first()
        doneBrukernotifikasjon.key() shouldBeEqualTo manglerBeskjedBestillingId
        doneBrukernotifikasjon.value().tilInaktiverVarselInstance().varselId shouldBeEqualTo manglerBeskjedBestillingId

        val cr = meldingKafkaConsumer.ventPåRecords(1).first()
        val doneDittSykefravaer: MeldingKafkaDto = cr.value().let { objectMapper.readValue(it) }

        cr.key() shouldBeEqualTo manglerMeldingBestillingId
        doneDittSykefravaer.lukkMelding.shouldNotBeNull()
    }

    @Test
    @Order(5)
    fun `Vi bestiller melding om mottatt inntektsmelding på Ditt Sykefravær`() {
        val meldingCR = meldingKafkaConsumer.ventPåRecords(1).first()
        meldingCR.key() shouldBeEqualTo mottatMeldingBestillingId

        val melding = objectMapper.readValue<MeldingKafkaDto>(meldingCR.value())
        melding.fnr shouldBeEqualTo fnr
        melding.lukkMelding.shouldBeNull()

        val opprettMelding = melding.opprettMelding.shouldNotBeNull()
        opprettMelding.meldingType shouldBeEqualTo "MOTTATT_INNTEKTSMELDING"
        opprettMelding.tekst shouldBeEqualTo "Vi har mottatt inntektsmeldingen fra Flex AS for sykefraværet som startet 16. mai 2022."
        opprettMelding.lenke.shouldBeNull()
        opprettMelding.lukkbar shouldBeEqualTo true
        opprettMelding.variant shouldBeEqualTo Variant.SUCCESS
        opprettMelding.synligFremTil.shouldNotBeNull()
        opprettMelding.synligFremTil!!.shouldBeAfter(OffsetDateTime.now().plusDays(13).toInstant())
        opprettMelding.synligFremTil!!.shouldBeBefore(OffsetDateTime.now().plusDays(15).toInstant())
    }

    @Test
    @Order(6)
    fun `Status historikken er riktig`() {
        val dbId = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId)!!.id!!
        val inntektsmelding = statusRepository.hentVedtaksperiodeMedStatusHistorikk(dbId)!!

        inntektsmelding.statusHistorikk.map { it.status } shouldBeEqualTo
            listOf(
                StatusVerdi.MANGLER_INNTEKTSMELDING,
                StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
                StatusVerdi.HAR_INNTEKTSMELDING,
                StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
                StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT,
                StatusVerdi.DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT,
            )

        val dbIdPeriode2 = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId2)!!.id!!
        val inntektsmelding2 = statusRepository.hentVedtaksperiodeMedStatusHistorikk(dbIdPeriode2)!!

        inntektsmelding2.statusHistorikk.map { it.status } shouldBeEqualTo
            listOf(
                StatusVerdi.MANGLER_INNTEKTSMELDING,
                StatusVerdi.HAR_PERIODE_RETT_FOER,
                StatusVerdi.HAR_INNTEKTSMELDING,
            )
    }

    @Test
    @Order(7)
    fun `Vi mottar enda en inntektsmelding`() {
        kafkaProducer.send(
            ProducerRecord(
                INNTEKTSMELDING_STATUS_TOPIC,
                fnr,
                InntektsmeldingKafkaDto(
                    id = UUID.randomUUID().toString(),
                    status = Status.HAR_INNTEKTSMELDING,
                    sykmeldt = fnr,
                    arbeidsgiver = orgNr,
                    vedtaksperiode =
                        Vedtaksperiode(
                            id = eksternId,
                            fom = fom,
                            tom = tom,
                        ),
                    tidspunkt = OffsetDateTime.now(),
                ).serialisertTilString(),
            ),
        ).get()

        val dbId = vedtaksperiodeRepository.findVedtaksperiodeDbRecordByEksternId(eksternId)!!.id!!

        await().atMost(5, TimeUnit.SECONDS).until {
            statusRepository.hentVedtaksperiodeMedStatusHistorikk(
                dbId,
            )?.statusHistorikk?.any { it.status == StatusVerdi.HAR_INNTEKTSMELDING }
        }
    }

    @Test
    @Order(8)
    fun `Ingen ny mottatt melding sendes`() {
        meldingKafkaConsumer.ventPåRecords(0)
    }
}
