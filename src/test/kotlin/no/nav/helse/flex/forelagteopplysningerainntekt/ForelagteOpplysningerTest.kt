package no.nav.helse.flex.forelagteopplysningerainntekt

import ForelagteOpplysningerMelding
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.melding.MeldingKafkaDto
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.organisasjon.Organisasjon
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.sykepengesoknad.Sykepengesoknad
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingDbRecord
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadDbRecord
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.shouldBeFalse
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.Random::class)
class ForelagteOpplysningerTest : FellesTestOppsett() {
    @Test
    fun `Tar imot og lagrer forelagte inntektsopplysninger fra ainntekt`() {
        val forelagteOpplysningerMelding =
            ForelagteOpplysningerMelding(
                vedtaksperiodeId = UUID.randomUUID().toString(),
                behandlingId = UUID.randomUUID().toString(),
                tidsstempel = LocalDateTime.now(),
                omregnetÅrsinntekt = 500000.0,
                skatteinntekter =
                    listOf(
                        ForelagteOpplysningerMelding.Skatteinntekt(
                            måned = YearMonth.of(2024, 1),
                            beløp = 42000.0,
                        ),
                        ForelagteOpplysningerMelding.Skatteinntekt(
                            måned = YearMonth.of(2024, 2),
                            beløp = 43000.0,
                        ),
                    ),
            )

        forelagteOpplysningerRepository.existsByVedtaksperiodeIdAndBehandlingId(
            vedtaksperiodeId = forelagteOpplysningerMelding.vedtaksperiodeId,
            behandlingId = forelagteOpplysningerMelding.behandlingId,
        ).shouldBeFalse()

        kafkaProducer.send(
            ProducerRecord(
                FORELAGTE_OPPLYSNINGER_TOPIC,
                null,
                forelagteOpplysningerMelding.vedtaksperiodeId,
                forelagteOpplysningerMelding.serialisertTilString(),
            ),
        ).get()

        await().atMost(10, TimeUnit.SECONDS).until {
            forelagteOpplysningerRepository.existsByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = forelagteOpplysningerMelding.vedtaksperiodeId,
                behandlingId = forelagteOpplysningerMelding.behandlingId,
            )
        }

        val record =
            forelagteOpplysningerRepository.findAll().toList().first {
                it.vedtaksperiodeId == forelagteOpplysningerMelding.vedtaksperiodeId
            }

        record.behandlingId `should be equal to` forelagteOpplysningerMelding.behandlingId
        val meldingFraDb: ForelagteOpplysningerMelding =
            objectMapper.readValue(record.forelagteOpplysningerMelding.value!!)

        meldingFraDb `should be equal to` forelagteOpplysningerMelding
    }

    @Test
    fun `Henter og sender ut brukernotifikasjon om forelagte inntektsopplysninger fra ainntekt`() {
        lagreSykepengesoknad(
            vedtaksperiodeId = "vedtaksperiode-test-opplysning",
            behandlingId = "behandling-test-opplysning",
        )

        ForelagteOpplysningerDbRecord(
            vedtaksperiodeId = "vedtaksperiode-test-opplysning",
            behandlingId = "behandling-test-opplysning",
            forelagteOpplysningerMelding =
                PGobject().apply {
                    type = "json"
                    value =
                        ForelagteOpplysningerMelding(
                            vedtaksperiodeId = "vedtaksperiode-test-opplysning",
                            behandlingId = "behandling-test-opplysning",
                            tidsstempel = LocalDateTime.parse("2024-01-16T00:00:00.00"),
                            omregnetÅrsinntekt = 0.0,
                            skatteinntekter = emptyList(),
                        ).serialisertTilString()
                },
            opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            forelagt = null,
        ).also {
            forelagteOpplysningerRepository.save(it)
        }

        sendForelagteOpplysningerCronjob.runMedParameter(Instant.parse("2024-11-15T12:00:00.00Z"))

        meldingKafkaConsumer.ventPåRecords(antall = 1)
            .first().let {
                it `should not be` null
                val kafkamelding: MeldingKafkaDto = objectMapper.readValue(it.value())
                kafkamelding.opprettMelding?.metadata?.get("vedtaksperiodeId")
                    ?.asText() `should be equal to` "vedtaksperiode-test-opplysning"
            }

        varslingConsumer.ventPåRecords(antall = 1)
    }

    private fun lagreSykepengesoknad(
        sykepengesoknadUuid: String = UUID.randomUUID().toString(),
        fnr: String = "testFnr0000",
        orgnummer: String = "test-org",
        vedtaksperiodeId: String = "vedtaksperiode-test-opplysning",
        behandlingId: String = "behandling-test-opplysning",
    ) {
        val soknad =
            Sykepengesoknad(
                sykepengesoknadUuid = sykepengesoknadUuid,
                orgnummer = orgnummer,
                soknadstype = "ARBEIDSTAKER",
                startSyketilfelle = LocalDate.of(2024, 1, 1),
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 16),
                fnr = fnr,
                sendt = Instant.parse("2024-01-16T00:00:00.00Z"),
                opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
            ).also {
                sykepengesoknadRepository.save(it)
            }

        val vedtaksperiodeBehandling =
            vedtaksperiodeBehandlingRepository.save(
                VedtaksperiodeBehandlingDbRecord(
                    opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    oppdatertDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteSpleisstatus = StatusVerdi.VENTER_PÅ_ARBEIDSGIVER,
                    sisteSpleisstatusTidspunkt = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteVarslingstatus = null,
                    sisteVarslingstatusTidspunkt = null,
                    vedtaksperiodeId = vedtaksperiodeId,
                    behandlingId = behandlingId,
                ),
            )

        vedtaksperiodeBehandlingSykepengesoknadRepository.save(
            VedtaksperiodeBehandlingSykepengesoknadDbRecord(
                vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id!!,
                sykepengesoknadUuid = soknad.sykepengesoknadUuid,
            ),
        )
    }
}

class SendForelagteOpplysningerOppgaveTest : FellesTestOppsett() {
    @Autowired
    lateinit var sendForelagteOpplysningerOppgave: SendForelagteOpplysningerOppgave

    @Test
    fun `En opplysning burde ikke bli forelagt dersom en tidligere opplysning er forelagt i nylig tid`() {
        val sammeOrgnummer = "test-org"
        val sammeFnr = "testFnr0000"
        lagreSykepengesoknad(
            vedtaksperiodeId = "vedtaksperiode-test-id",
            behandlingId = "behandling-test-id",
            orgnummer = sammeOrgnummer,
            fnr = sammeFnr,
        )
        lagreSykepengesoknad(
            vedtaksperiodeId = "vedtaksperiode-test-id2",
            behandlingId = "behandling-test-id2",
            orgnummer = sammeOrgnummer,
            fnr = sammeFnr,
        )

        val tidligereForelagtTidspunkt = Instant.parse("2024-01-01T00:00:00.00Z")
        val skalIkkeVarsleTidspunkt = Instant.parse("2024-01-28T00:00:00.00Z")
        val skalVarsleTidspunkt = Instant.parse("2024-01-29T00:00:00.00Z")

        ForelagteOpplysningerDbRecord(
            vedtaksperiodeId = "vedtaksperiode-test-id",
            behandlingId = "behandling-test-id",
            forelagteOpplysningerMelding =
                PGobject().apply {
                    type = "json"
                    value = "{}"
                },
            opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            forelagt = tidligereForelagtTidspunkt,
        ).also {
            forelagteOpplysningerRepository.save(it)
        }

        val nyForelagtOpplysning =
            ForelagteOpplysningerDbRecord(
                vedtaksperiodeId = "vedtaksperiode-test-id2",
                behandlingId = "behandling-test-id2",
                forelagteOpplysningerMelding =
                    PGobject().apply {
                        type = "json"
                        value = "{}"
                    },
                opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
                forelagt = null,
            )
        var nyForelagtOpplysningLagret = forelagteOpplysningerRepository.save(nyForelagtOpplysning)

        sendForelagteOpplysningerOppgave.sendForelagteOpplysninger(nyForelagtOpplysningLagret.id!!, skalIkkeVarsleTidspunkt)

        meldingKafkaConsumer.ventPåRecords(antall = 0)
        varslingConsumer.ventPåRecords(antall = 0)

        forelagteOpplysningerRepository.delete(nyForelagtOpplysningLagret)
        nyForelagtOpplysningLagret = forelagteOpplysningerRepository.save(nyForelagtOpplysning)

        sendForelagteOpplysningerOppgave.sendForelagteOpplysninger(nyForelagtOpplysningLagret.id!!, skalVarsleTidspunkt)

        meldingKafkaConsumer.ventPåRecords(antall = 1)
        varslingConsumer.ventPåRecords(antall = 1)
    }

    private fun lagreSykepengesoknad(
        sykepengesoknadUuid: String = UUID.randomUUID().toString(),
        fnr: String = "testFnr0000",
        orgnummer: String = "test-org",
        vedtaksperiodeId: String = "vedtaksperiode-test-opplysning",
        behandlingId: String = "behandling-test-opplysning",
    ) {
        val soknad =
            Sykepengesoknad(
                sykepengesoknadUuid = sykepengesoknadUuid,
                orgnummer = orgnummer,
                soknadstype = "ARBEIDSTAKER",
                startSyketilfelle = LocalDate.of(2024, 1, 1),
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 16),
                fnr = fnr,
                sendt = Instant.parse("2024-01-16T00:00:00.00Z"),
                opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
            ).also {
                sykepengesoknadRepository.save(it)
            }

        val vedtaksperiodeBehandling =
            vedtaksperiodeBehandlingRepository.save(
                VedtaksperiodeBehandlingDbRecord(
                    opprettetDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    oppdatertDatabase = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteSpleisstatus = StatusVerdi.VENTER_PÅ_ARBEIDSGIVER,
                    sisteSpleisstatusTidspunkt = Instant.parse("2024-01-16T00:00:00.00Z"),
                    sisteVarslingstatus = null,
                    sisteVarslingstatusTidspunkt = null,
                    vedtaksperiodeId = vedtaksperiodeId,
                    behandlingId = behandlingId,
                ),
            )

        vedtaksperiodeBehandlingSykepengesoknadRepository.save(
            VedtaksperiodeBehandlingSykepengesoknadDbRecord(
                vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id!!,
                sykepengesoknadUuid = soknad.sykepengesoknadUuid,
            ),
        )

        Organisasjon(
            orgnummer = orgnummer,
            navn = "Organisasjonen",
            opprettet = Instant.parse("2024-01-01T00:00:00.00Z"),
            oppdatert = Instant.parse("2024-01-01T00:00:00.00Z"),
            oppdatertAv = "personen",
        ).also {
            if (organisasjonRepository.findByOrgnummer(orgnummer) == null) {
                organisasjonRepository.save(it)
            }
        }
    }
}