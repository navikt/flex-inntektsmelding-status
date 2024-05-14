package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.database.LockRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ProsseserKafkaMeldingFraSpleiselaget(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val lockRepository: LockRepository,
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
        if (vedtaksperiodeBehandling == null) {
            if (kafkaDto.status == Behandlingstatustype.OPPRETTET) {
                if (kafkaDto.eksternSøknadId == null) {
                    val message =
                        "Mangler søknad id for behandlingId ${kafkaDto.behandlingId} og vedtaksperiodeId ${kafkaDto.vedtaksperiodeId}"
                    throw RuntimeException(
                        message,
                    )
                }

                val vedtaksperiodeBehandlingDbRecord =
                    vedtaksperiodeBehandlingRepository.save(
                        VedtaksperiodeBehandlingDbRecord(
                            behandlingId = kafkaDto.behandlingId,
                            vedtaksperiodeId = kafkaDto.vedtaksperiodeId,
                            opprettetDatabase = Instant.now(),
                            oppdatert = Instant.now(),
                            sisteSpleisstatus = kafkaDto.status.tilStatusVerdi(),
                            sisteVarslingstatus = null,
                            sykepengesoknadUuid = kafkaDto.eksternSøknadId,
                        ),
                    )
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

            log.warn(
                "Fant ikke vedtaksperiodeBehandling for vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} " +
                    "og behandlingId ${kafkaDto.behandlingId}. Det kan skje for perioder opprettet før mai 2024",
            )
            return
        }

        val soknad = sykepengesoknadRepository.findBySykepengesoknadUuid(vedtaksperiodeBehandling.sykepengesoknadUuid)
        soknad?.let {
            // Låser fødselsnummeret hvis vi har en søknad
            lockRepository.settAdvisoryTransactionLock(soknad.fnr)
        }

        fun oppdaterdatabaseMedSisteStatus() {
            vedtaksperiodeBehandlingRepository.save(
                vedtaksperiodeBehandling.copy(
                    sisteSpleisstatus = kafkaDto.status.tilStatusVerdi(),
                    oppdatert = Instant.now(),
                ),
            )
            vedtaksperiodeBehandlingStatusRepository.save(
                VedtaksperiodeBehandlingStatusDbRecord(
                    vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id!!,
                    opprettetDatabase = Instant.now(),
                    tidspunkt = kafkaDto.tidspunkt.toInstant(),
                    status = kafkaDto.status.tilStatusVerdi(),
                    dittSykefravaerMeldingId = null,
                    brukervarselId = null,
                ),
            )
        }
        kafkaDto.erTillattStatusEndring(vedtaksperiodeBehandling.sisteSpleisstatus)
        when (kafkaDto.status) {
            Behandlingstatustype.OPPRETTET -> {
                log.warn(
                    "Skal ikke motta status OPPRETTET for vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} Den skal allerede være opprettet",
                )
            }

            Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER -> {
                oppdaterdatabaseMedSisteStatus()
            }

            Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER -> {
                oppdaterdatabaseMedSisteStatus()
                // TODO fjern sendte mangler im varsler
            }

            Behandlingstatustype.BEHANDLES_UTENFOR_SPEIL -> {
                oppdaterdatabaseMedSisteStatus()
                // TODO fjern sendte mangler im varsler
                // TODO fjern sendte forsinket saksbehandlingsvarsler
            }

            Behandlingstatustype.FERDIG -> {
                oppdaterdatabaseMedSisteStatus()
                // TODO fjern sendte mangler im varsler
                // TODO fjern sendte forsinket saksbehandlingsvarsler
            }
        }
    }

    fun Behandlingstatusmelding.erTillattStatusEndring(gammelStatus: StatusVerdi) {
        fun sjekkStatus(forventetGammelVerdi: StatusVerdi) {
            if (gammelStatus != forventetGammelVerdi) {
                log.warn(
                    "Forventet ikke gammel status $gammelStatus for ny status ${this.status} for " +
                        "vedtaksperiodeid ${this.vedtaksperiodeId} og behandlign id ${this.behandlingId}",
                )
            }
        }

        when (this.status) {
            Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER -> {
                sjekkStatus(StatusVerdi.OPPRETTET)
            }
            Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER -> {
                sjekkStatus(StatusVerdi.VENTER_PÅ_ARBEIDSGIVER)
            }
            Behandlingstatustype.FERDIG -> {
                sjekkStatus(StatusVerdi.VENTER_PÅ_SAKSBEHANDLER)
            }
            else -> {
            }
        }
    }
}
