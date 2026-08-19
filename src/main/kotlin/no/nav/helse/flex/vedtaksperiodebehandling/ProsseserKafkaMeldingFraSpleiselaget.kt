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

        val sisteSpleisstatusTidspunkt = kafkaDto.tidspunkt.toInstant()
        val sisteSpleisstatus = kafkaDto.status.tilSpleisStatus()
        val now = Instant.now()

        if (vedtaksperiodeBehandling == null) {
            val vedtaksperiodeBehandlingDbRecord =
                vedtaksperiodeBehandlingRepository.save(
                    VedtaksperiodeBehandlingDbRecord(
                        behandlingId = kafkaDto.behandlingId,
                        vedtaksperiodeId = kafkaDto.vedtaksperiodeId,
                        opprettetDatabase = now,
                        oppdatertDatabase = now,
                        sisteSpleisstatus = sisteSpleisstatus,
                        sisteSpleisstatusTidspunkt = sisteSpleisstatusTidspunkt,
                        sisteVarslingstatus = null,
                        sisteVarslingstatusTidspunkt = null,
                    ),
                )

            lagreSøknadIder(vedtaksperiodeBehandlingDbRecord)
            log.info(
                "Lagret ny vedtaksperiodeBehandling vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} med status $sisteSpleisstatus og tidspunkt $sisteSpleisstatusTidspunkt",
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

        if (kafkaDto.status == Behandlingstatustype.OPPRETTET) {
            log.warn(
                "Skal ikke motta status OPPRETTET for vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} Den skal allerede være opprettet",
            )
            return
        }

        val oppdatertStatusVedtaksperiodeBehandling =
            vedtaksperiodeBehandlingRepository.save(
                vedtaksperiodeBehandling.copy(
                    sisteSpleisstatus = sisteSpleisstatus,
                    sisteSpleisstatusTidspunkt = sisteSpleisstatusTidspunkt,
                    oppdatertDatabase = now,
                ),
            )

        if (vedtaksperiodeBehandling.sisteSpleisstatus != sisteSpleisstatus) {
            log.info(
                "Oppdatert vedtaksperiodeBehandling vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} med status $sisteSpleisstatus og tidspunkt $sisteSpleisstatusTidspunkt",
            )
        }

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
