package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.varselutsending.MeldingOgBrukervarselDone
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ProsseserKafkaMeldingFraSpleiselaget(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val lockRepository: LockRepository,
    private val meldingOgBrukervarselDone: MeldingOgBrukervarselDone,
) {
    val log = logger()

    @Transactional
    fun prosesserKafkaMelding(kafkaDto: Behandlingstatusmelding) {
        lockRepository.settAdvisoryTransactionLock(kafkaDto.vedtaksperiodeId)

        val vedtaksperiodeBehandling =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = kafkaDto.vedtaksperiodeId,
                behandlingId = kafkaDto.behandlingId,
            )

        fun lagreSøknadIder(vedtaksperiodeBehandlingDbRecord: VedtaksperiodeBehandlingDbRecord) {
            kafkaDto.eksterneSøknadIder.forEach { eksternSøknadId ->

                val eksternSøknadForDenneBehandlingenMangler =
                    vedtaksperiodeBehandlingSykepengesoknadRepository
                        .findBySykepengesoknadUuidIn(
                            listOf(eksternSøknadId),
                        ).none { it.vedtaksperiodeBehandlingId == vedtaksperiodeBehandlingDbRecord.id }

                if (eksternSøknadForDenneBehandlingenMangler) {
                    vedtaksperiodeBehandlingSykepengesoknadRepository.save(
                        VedtaksperiodeBehandlingSykepengesoknadDbRecord(
                            vedtaksperiodeBehandlingId = vedtaksperiodeBehandlingDbRecord.id!!,
                            sykepengesoknadUuid = eksternSøknadId,
                        ),
                    )
                }
            }
        }
        if (vedtaksperiodeBehandling == null) {
            val vedtaksperiodeBehandlingDbRecord =
                vedtaksperiodeBehandlingRepository.save(
                    VedtaksperiodeBehandlingDbRecord(
                        behandlingId = kafkaDto.behandlingId,
                        vedtaksperiodeId = kafkaDto.vedtaksperiodeId,
                        opprettetDatabase = Instant.now(),
                        oppdatertDatabase = Instant.now(),
                        sisteSpleisstatus = kafkaDto.status.tilStatusVerdi(),
                        sisteSpleisstatusTidspunkt = kafkaDto.tidspunkt.toInstant(),
                        sisteVarslingstatus = null,
                        sisteVarslingstatusTidspunkt = null,
                    ),
                )

            lagreSøknadIder(vedtaksperiodeBehandlingDbRecord)

            vedtaksperiodeBehandlingStatusRepository.save(
                VedtaksperiodeBehandlingStatusDbRecord(
                    vedtaksperiodeBehandlingId = vedtaksperiodeBehandlingDbRecord.id!!,
                    opprettetDatabase = Instant.now(),
                    tidspunkt = kafkaDto.tidspunkt.toInstant(),
                    status = kafkaDto.status.tilStatusVerdi(),
                    dittSykefravaerMeldingId = null,
                    brukervarselId = null,
                ),
            )

            return
        }

        val soknadIder =
            vedtaksperiodeBehandlingSykepengesoknadRepository
                .findByVedtaksperiodeBehandlingIdIn(
                    listOf(vedtaksperiodeBehandling.id!!),
                ).map { it.sykepengesoknadUuid }

        val soknad = sykepengesoknadRepository.findBySykepengesoknadUuidIn(soknadIder).firstOrNull()

        soknad?.let {
            // Låser fødselsnummeret hvis vi har en søknad
            lockRepository.settAdvisoryTransactionLock(soknad.fnr)
        }
        lagreSøknadIder(vedtaksperiodeBehandling)

        fun oppdaterdatabaseMedSisteStatus(): VedtaksperiodeBehandlingDbRecord {
            vedtaksperiodeBehandlingStatusRepository.save(
                VedtaksperiodeBehandlingStatusDbRecord(
                    vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id,
                    opprettetDatabase = Instant.now(),
                    tidspunkt = kafkaDto.tidspunkt.toInstant(),
                    status = kafkaDto.status.tilStatusVerdi(),
                    dittSykefravaerMeldingId = null,
                    brukervarselId = null,
                ),
            )
            return vedtaksperiodeBehandlingRepository.save(
                vedtaksperiodeBehandling.copy(
                    sisteSpleisstatus = kafkaDto.status.tilStatusVerdi(),
                    sisteSpleisstatusTidspunkt = kafkaDto.tidspunkt.toInstant(),
                    oppdatertDatabase = Instant.now(),
                ),
            )
        }
        if (kafkaDto.status == Behandlingstatustype.OPPRETTET) {
            log.warn(
                "Skal ikke motta status OPPRETTET for vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} Den skal allerede være opprettet",
            )
            return
        }

        val oppdatertStatusVedtaksperiodeBehandling = oppdaterdatabaseMedSisteStatus()

        when (kafkaDto.status) {
            Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER -> {
                // Ingenting spesielt å gjøre synkront
            }

            Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER -> {
                meldingOgBrukervarselDone.doneSendteManglerImVarsler(
                    oppdatertStatusVedtaksperiodeBehandling,
                    soknad?.fnr,
                )
            }

            Behandlingstatustype.VENTER_PÅ_ANNEN_PERIODE -> {
                meldingOgBrukervarselDone.doneSendteManglerImVarsler(
                    oppdatertStatusVedtaksperiodeBehandling,
                    soknad?.fnr,
                )
            }

            Behandlingstatustype.BEHANDLES_UTENFOR_SPEIL -> {
                meldingOgBrukervarselDone.doneSendteManglerImVarsler(
                    oppdatertStatusVedtaksperiodeBehandling,
                    soknad?.fnr,
                )
                meldingOgBrukervarselDone.doneForsinketSbVarsel(
                    oppdatertStatusVedtaksperiodeBehandling,
                    soknad?.fnr,
                )
            }

            Behandlingstatustype.FERDIG -> {
                meldingOgBrukervarselDone.doneSendteManglerImVarsler(
                    oppdatertStatusVedtaksperiodeBehandling,
                    soknad?.fnr,
                )
                meldingOgBrukervarselDone.doneForsinketSbVarsel(
                    oppdatertStatusVedtaksperiodeBehandling,
                    soknad?.fnr,
                )
            }

            Behandlingstatustype.OPPRETTET -> {
                throw IllegalStateException()
            }
        }
    }
}
