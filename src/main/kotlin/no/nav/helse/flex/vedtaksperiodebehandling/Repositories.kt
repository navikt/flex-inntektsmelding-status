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

    fun findByVedtaksperiodeId(vedtaksperiodeId: String): List<VedtaksperiodeBehandlingDbRecord>

    fun findByIdIn(id: List<String>): List<VedtaksperiodeBehandlingDbRecord>

    @Query(
        """
            select distinct fnr
            from 
            (
                select max(s.fnr) as fnr, max(s.sendt) as sendt
                from vedtaksperiode_behandling v, sykepengesoknad s, vedtaksperiode_behandling_sykepengesoknad vbs
                WHERE vbs.sykepengesoknad_uuid = s.sykepengesoknad_uuid 
                AND vbs.vedtaksperiode_behandling_id = v.id
                AND v.siste_spleisstatus = 'VENTER_PÅ_ARBEIDSGIVER' 
                AND v.siste_varslingstatus is null 
                group by v.vedtaksperiode_id, v.behandling_id
            ) as sub
            where sendt < :sendtFoer

        """,
    )
    fun finnPersonerMedPerioderSomVenterPaaArbeidsgiver(
        @Param("sendtFoer") sendtFoer: Instant,
    ): List<String>

    @Query(
        """
            select distinct fnr
            from 
            (
                select max(s.fnr) as fnr, max(s.sendt) as sendt
                from vedtaksperiode_behandling v, sykepengesoknad s, vedtaksperiode_behandling_sykepengesoknad vbs
                WHERE vbs.sykepengesoknad_uuid = s.sykepengesoknad_uuid 
                AND vbs.vedtaksperiode_behandling_id = v.id
                AND v.siste_spleisstatus = 'VENTER_PÅ_ARBEIDSGIVER' 
                AND v.siste_varslingstatus = 'VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE' 
                group by v.vedtaksperiode_id, v.behandling_id
            ) as sub
            where sendt < :sendtFoer
        """,
    )
    fun finnPersonerMedForsinketSaksbehandlingGrunnetManglendeInntektsmelding(
        @Param("sendtFoer") sendtFoer: Instant,
    ): List<String>

    @Query(
        """
            select distinct fnr
            from 
            (
                select max(s.fnr) as fnr, max(s.sendt) as sendt
                from vedtaksperiode_behandling v, sykepengesoknad s, vedtaksperiode_behandling_sykepengesoknad vbs
                WHERE vbs.sykepengesoknad_uuid = s.sykepengesoknad_uuid 
                AND vbs.vedtaksperiode_behandling_id = v.id
                  AND v.siste_spleisstatus = 'VENTER_PÅ_SAKSBEHANDLER' 
                  AND (v.siste_varslingstatus not in (
                    'VARSLET_VENTER_PÅ_SAKSBEHANDLER', 
                    'REVARSLET_VENTER_PÅ_SAKSBEHANDLER',
                    'VARSLER_IKKE_GRUNNET_FULL_REFUSJON'
                 ) or v.siste_varslingstatus is null)
                group by v.vedtaksperiode_id, v.behandling_id
            ) as sub
            where sendt < :sendtFoer
        """,
    )
    fun finnPersonerMedForsinketSaksbehandlingGrunnetVenterPaSaksbehandler(
        @Param("sendtFoer") sendtFoer: Instant,
    ): List<String>

    @Query(
        """
        select distinct max(s.fnr) as fnr
        from vedtaksperiode_behandling v, sykepengesoknad s, vedtaksperiode_behandling_sykepengesoknad vbs
        WHERE vbs.sykepengesoknad_uuid = s.sykepengesoknad_uuid 
        AND vbs.vedtaksperiode_behandling_id = v.id
          AND v.siste_spleisstatus = 'VENTER_PÅ_SAKSBEHANDLER' 
          AND v.siste_varslingstatus  in (
            'VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE', 
            'REVARSLET_VENTER_PÅ_SAKSBEHANDLER'
         )
         AND  siste_varslingstatus_tidspunkt < :varsletFoer
        group by v.vedtaksperiode_id, v.behandling_id
        """,
    )
    fun finnPersonerForRevarslingSomVenterPåSaksbehandler(
        @Param("varsletFoer") varsletFoer: Instant,
    ): List<String>
}

@Table("vedtaksperiode_behandling")
data class VedtaksperiodeBehandlingDbRecord(
    @Id
    val id: String? = null,
    val opprettetDatabase: Instant,
    val oppdatertDatabase: Instant,
    val sisteSpleisstatus: StatusVerdi,
    val sisteSpleisstatusTidspunkt: Instant,
    val sisteVarslingstatus: StatusVerdi?,
    val sisteVarslingstatusTidspunkt: Instant?,
    val vedtaksperiodeId: String,
    val behandlingId: String,
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

    fun findBySykepengesoknadUuidIn(ider: List<String>): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord>

    fun findByFnrIn(fnr: String): List<VedtaksperiodeBehandlingSykepengesoknadDbRecord>
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
    VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE,
    VARSLET_MANGLER_INNTEKTSMELDING_FØRSTE_DONE,
    VARSLET_MANGLER_INNTEKTSMELDING_ANDRE,
    VARSLET_MANGLER_INNTEKTSMELDING_ANDRE_DONE,
    VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE,
    VARSLET_VENTER_PÅ_SAKSBEHANDLER_FØRSTE_DONE,
    OPPRETTET,
    VENTER_PÅ_ARBEIDSGIVER,
    VENTER_PÅ_SAKSBEHANDLER,
    VENTER_PÅ_ANNEN_PERIODE,
    FERDIG,
    BEHANDLES_UTENFOR_SPEIL,
    REVARSLET_VENTER_PÅ_SAKSBEHANDLER,
    REVARSLET_VENTER_PÅ_SAKSBEHANDLER_DONE,
    VARSLER_IKKE_GRUNNET_FULL_REFUSJON,
    VARSLET_FORSINKET_PA_ANNEN_ORGNUMMER,
    VARSLET_MANGLER_INNTEKTSMELDING, // finnes i dev
    VARSLET_MANGLER_INNTEKTSMELDING_DONE, // finnes i dev
    VARSLET_VENTER_PÅ_SAKSBEHANDLER, // finnes i dev
    VARSLET_MANGLER_INNTEKTSMELDING_15, // finnes i dev
    VARSLET_MANGLER_INNTEKTSMELDING_15_DONE, // finnes i dev
    VARSLET_MANGLER_INNTEKTSMELDING_28, // finnes i dev
    VARSLET_MANGLER_INNTEKTSMELDING_28_DONE, // finnes i dev
    VARSLET_VENTER_PÅ_SAKSBEHANDLER_28, // finnes i dev
    VARSLET_VENTER_PÅ_SAKSBEHANDLER_28_DONE, // finnes i dev
}
