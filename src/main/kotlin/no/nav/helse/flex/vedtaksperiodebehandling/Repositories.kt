package no.nav.helse.flex.vedtaksperiodebehandling

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VedtaksperiodeBehandlingRepository : CrudRepository<VedtaksperiodeBehandlingDbRecord, String> {
    fun findByVedtaksperiodeIdAndBehandlingId(
        vedtaksperiodeId: String,
        behandlingId: String,
    ): VedtaksperiodeBehandlingDbRecord?

    // fun findByVedtaksperiodeIdIn(ider: List<String>): List<VedtaksperiodeBehandlingDbRecord> // todo slett
    fun findByIdIn(id: List<String>): List<VedtaksperiodeBehandlingDbRecord>
}

@Table("vedtaksperiode_behandling")
data class VedtaksperiodeBehandlingDbRecord(
    @Id
    val id: String? = null,
    val opprettetDatabase: Instant,
    val oppdatert: Instant,
    val sisteSpleisstatus: StatusVerdi,
    val sisteVarslingstatus: StatusVerdi?,
    val vedtaksperiodeId: String,
    val behandlingId: String,
    // val sykepengesoknadUuid: String,
)

@Table("vedtaksperiode_behandling_sykepengesoknad")
data class VedtaksperiodeBehandlingSykepengesoknadDbRecord(
    @Id
    val id: String? = null,
    val vedtaksperiodeBehandlingId: String,
    val sykepengesoknadUuid: String,
)

@Repository
interface VedtaksperiodeBehandlingSykepengesoknadRepository : CrudRepository<VedtaksperiodeBehandlingSykepengesoknadDbRecord, String> {
    fun findByVedtaksperiodeBehandlingIdIn(ider: List<String>): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord>

    fun findByVedtaksperiodeBehandlingId(id: String): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord>

    fun findBySykepengesoknadUuid(id: String): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord>

    fun findBySykepengesoknadUuidIn(ider: List<String>): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord> // må kansjke
}

@Repository
interface VedtaksperiodeBehandlingStatusRepository : CrudRepository<VedtaksperiodeBehandlingStatusDbRecord, String> {
    fun findByVedtaksperiodeBehandlingIdIn(ider: List<String>): List<VedtaksperiodeBehandlingStatusDbRecord>
}

@Table("vedtaksperiode_behandling_status")
data class VedtaksperiodeBehandlingStatusDbRecord(
    @Id
    val id: String? = null,
    val vedtaksperiodeBehandlingId: String,
    val opprettetDatabase: Instant,
    val tidspunkt: Instant,
    val status: StatusVerdi,
    val brukervarselId: String?,
    val dittSykefravaerMeldingId: String?,
)

enum class StatusVerdi {
    OPPRETTET,
    VENTER_PÅ_ARBEIDSGIVER,
    VENTER_PÅ_SAKSBEHANDLER,
    FERDIG,
    BEHANDLES_UTENFOR_SPEIL,
    BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
    BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
    DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT,
    DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_DONE_SENDT,
    DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_SENDT,
    DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_LUKKET,
    DITT_SYKEFRAVAER_MOTTATT_INNTEKTSMELDING_DONE_SENDT,
}

@Repository
interface PeriodeStatusRepository : org.springframework.data.repository.Repository<StatusQueryResult, String> {
    @Query(
        """
            SELECT s.sykepengesoknad_uuid, s.fnr, s.sendt 
            FROM vedtaksperiode_behandling v, sykepengesoknad s, vedtaksperiode_behandling_sykepengesoknad vbs
            WHERE vbs.sykepengesoknad_uuid = s.sykepengesoknad_uuid 
            AND vbs.vedtaksperiode_behandling_id = v.id
            AND v.siste_spleisstatus = 'VENTER_PÅ_ARBEIDSGIVER' 
            AND v.siste_varslingstatus is null 
            AND s.sendt < :sendtFoer

        """,
    )
    fun finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
        @Param("sendtFoer") sendtFoer: Instant,
    ): List<StatusQueryResult>
}

data class StatusQueryResult(
    val sykepengesoknadUuid: String,
    val fnr: String,
    val sendt: Instant,
)
