package no.nav.helse.flex.vedtaksperiode

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate

@Repository
class StatusRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun hentVedtaksperiodeMedStatusHistorikk(vedtaksperiodeDbId: String): VedtaksperiodeMedStatusHistorikk? {
        return namedParameterJdbcTemplate.queryForObject(
            """
            SELECT vp.id,
                   vp.fnr,
                   vp.org_nr,
                   vp.org_navn,
                   vp.opprettet,
                   vp.vedtak_fom,
                   vp.vedtak_tom,
                   vp.ekstern_timestamp,
                   vp.ekstern_id,
                   status.id AS status_id,
                   status.status,
                   status.opprettet AS status_opprettet
            FROM vedtaksperiode vp
            LEFT JOIN vedtaksperiode_status status ON status.vedtaksperiode_db_id = vp.id
            WHERE vp.id = :vedtaksperiode_db_id
            ORDER BY status_opprettet
            """,
            MapSqlParameterSource().addValue("vedtaksperiode_db_id", vedtaksperiodeDbId),
        ) { resultSet, _ ->
            resultSet.tilVedtaksperiodeMedStatusHistorikk()
        }
    }

    fun hentAlleMedNyesteStatus(vararg harStatus: StatusVerdi): List<VedtaksperiodeMedStatus> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT vp.id,
                   vp.fnr,
                   vp.org_nr,
                   vp.org_navn,
                   vp.opprettet,
                   vp.vedtak_fom,
                   vp.vedtak_tom,
                   vp.ekstern_timestamp,
                   vp.ekstern_id,
                   status.vedtaksperiode_db_id,
                   status.status,
                   status.opprettet AS status_opprettet
            FROM vedtaksperiode_status status
                     INNER JOIN (SELECT vedtaksperiode_db_id, max(opprettet) AS opprettet
                                 FROM vedtaksperiode_status
                                 GROUP BY vedtaksperiode_db_id) max_status
                                    ON status.vedtaksperiode_db_id = max_status.vedtaksperiode_db_id AND
                                       status.opprettet = max_status.opprettet
                     INNER JOIN vedtaksperiode vp ON vp.id = status.vedtaksperiode_db_id
            WHERE status.status IN (:harStatus)  
              AND ferdig_behandlet IS NULL
            ORDER BY opprettet
            """,
            MapSqlParameterSource().addValue("harStatus", harStatus.asList(), Types.VARCHAR),
        ) { resultSet, _ ->
            resultSet.tilVedtaksperiodeMedStatus()
        }
    }

    fun hentAlleForPerson(
        fnr: String,
        orgNr: String,
    ): List<VedtaksperiodeMedStatus> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT vp.id,
                   vp.fnr,
                   vp.org_nr,
                   vp.org_navn,
                   vp.opprettet,
                   vp.vedtak_fom,
                   vp.vedtak_tom,
                   vp.ekstern_timestamp,
                   vp.ekstern_id,
                   status.vedtaksperiode_db_id,
                   status.status,
                   status.opprettet AS status_opprettet
            FROM vedtaksperiode_status status
                     INNER JOIN (SELECT vedtaksperiode_db_id, max(opprettet) AS opprettet
                                 FROM vedtaksperiode_status
                                 WHERE vedtaksperiode_db_id IN 
                                    (SELECT id FROM vedtaksperiode
                                     WHERE fnr = :fnr
                                       AND org_nr = :orgNr)
                                 GROUP BY vedtaksperiode_db_id) max_status
                                ON status.vedtaksperiode_db_id = max_status.vedtaksperiode_db_id AND
                                   status.opprettet = max_status.opprettet
                     INNER JOIN vedtaksperiode vp ON vp.id = status.vedtaksperiode_db_id
            ORDER BY opprettet
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr)
                .addValue("orgNr", orgNr),
        ) { resultSet, _ ->
            resultSet.tilVedtaksperiodeMedStatus()
        }
    }

    fun hentAlleOrgnrForPerson(fnr: String): Set<String> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT distinct vp.org_nr
            FROM vedtaksperiode vp
            where vp.fnr = :fnr
            """,
            MapSqlParameterSource()
                .addValue("fnr", fnr),
        ) { resultSet, _ ->
            resultSet.tilOrgnr()
        }.toSet()
    }

    private fun ResultSet.tilOrgnr(): String {
        return getString("org_nr")
    }

    private fun ResultSet.tilVedtaksperiodeMedStatus(): VedtaksperiodeMedStatus {
        return VedtaksperiodeMedStatus(
            id = getString("id"),
            fnr = getString("fnr"),
            orgNr = getString("org_nr"),
            orgNavn = getString("org_navn"),
            vedtaksperiodeDbId = getString("vedtaksperiode_db_id"),
            opprettet = getTimestamp("opprettet").toInstant(),
            vedtakFom = getDate("vedtak_fom").toLocalDate(),
            vedtakTom = getDate("vedtak_tom").toLocalDate(),
            eksternTimestamp = getTimestamp("ekstern_timestamp").toInstant(),
            eksternId = getString("ekstern_id"),
            status = StatusVerdi.valueOf(getString("status")),
            statusOpprettet = getTimestamp("status_opprettet").toInstant(),
        )
    }

    private fun ResultSet.tilVedtaksperiodeMedStatusHistorikk(): VedtaksperiodeMedStatusHistorikk {
        val statusVerdier =

            if (this.getString("status") != null) {
                mutableListOf(mapVedtaksperiodeStatus())
            } else {
                mutableListOf()
            }

        val inntektsmelding =
            VedtaksperiodeMedStatusHistorikk(
                id = getString("id"),
                fnr = getString("fnr"),
                orgNr = getString("org_nr"),
                orgNavn = getString("org_navn"),
                opprettet = getTimestamp("opprettet").toInstant(),
                vedtakFom = getDate("vedtak_fom").toLocalDate(),
                vedtakTom = getDate("vedtak_tom").toLocalDate(),
                eksternTimestamp = getTimestamp("ekstern_timestamp").toInstant(),
                eksternId = getString("ekstern_id"),
                statusHistorikk = statusVerdier,
            )

        while (next()) {
            statusVerdier.add(mapVedtaksperiodeStatus())
        }

        return inntektsmelding
    }

    private fun ResultSet.mapVedtaksperiodeStatus() =
        StatusHistorikk(
            id = getString("status_id"),
            status = StatusVerdi.valueOf(getString("status")),
            opprettet = getTimestamp("status_opprettet").toInstant(),
        )
}

data class VedtaksperiodeMedStatus(
    val id: String,
    val fnr: String,
    val orgNr: String,
    val orgNavn: String,
    val opprettet: Instant,
    val vedtakFom: LocalDate,
    val vedtakTom: LocalDate,
    val eksternTimestamp: Instant,
    val vedtaksperiodeDbId: String,
    val eksternId: String,
    val status: StatusVerdi,
    val statusOpprettet: Instant,
)

data class VedtaksperiodeMedStatusHistorikk(
    val id: String,
    val fnr: String,
    val orgNr: String,
    val orgNavn: String,
    val opprettet: Instant,
    val vedtakFom: LocalDate,
    val vedtakTom: LocalDate,
    val eksternTimestamp: Instant,
    val eksternId: String,
    val statusHistorikk: List<StatusHistorikk>,
) {
    fun harBeskjedSendt() = statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }

    fun harMeldingSendt() = statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }

    fun harBeskjedDonet() = statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT }

    fun harMeldingDonet() = statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT }

    fun alleBrukernotifikasjonerErDonet(): Boolean {
        val brukernotifikasjoner =
            statusHistorikk.filter {
                it.status in
                    listOf(
                        StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT,
                        StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT,
                    )
            }

        if (brukernotifikasjoner.isEmpty()) {
            return true
        }

        return brukernotifikasjoner.size % 2 == 0 &&
            brukernotifikasjoner.last().status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT
    }
}

data class StatusHistorikk(
    val id: String,
    val status: StatusVerdi,
    val opprettet: Instant,
)
