package no.nav.helse.flex.inntektsmelding

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate

@Repository
class StatusRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {

    fun hentInntektsmeldingMedStatusHistorikk(inntektsmeldingId: String): InntektsmeldingMedStatusHistorikk? {
        return namedParameterJdbcTemplate.queryForObject(
            """
            SELECT im.id,
                   im.fnr,
                   im.org_nr,
                   im.org_navn,
                   im.opprettet,
                   im.vedtak_fom,
                   im.vedtak_tom,
                   im.ekstern_timestamp,
                   im.ekstern_id,
                   status.id AS status_id,
                   status.status,
                   status.opprettet AS status_opprettet
            FROM inntektsmelding im
            LEFT JOIN inntektsmelding_status status on status.inntektsmelding_id = im.id
            WHERE im.id = :inntektsmelding_id
            ORDER BY status_opprettet
            """,
            MapSqlParameterSource().addValue("inntektsmelding_id", inntektsmeldingId)
        ) { resultSet, _ ->
            resultSet.tilInntektsmeldingMedStatusHistorikk()
        }
    }

    fun hentAlleMedNyesteStatus(vararg harStatus: StatusVerdi): List<InntektsmeldingMedStatus> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT im.id,
                   im.fnr,
                   im.org_nr,
                   im.org_navn,
                   im.opprettet,
                   im.vedtak_fom,
                   im.vedtak_tom,
                   im.ekstern_timestamp,
                   im.ekstern_id,
                   status.status,
                   status.opprettet AS status_opprettet
            FROM inntektsmelding_status status
                     INNER JOIN (SELECT inntektsmelding_id, max(opprettet) AS opprettet
                                 FROM inntektsmelding_status
                                 GROUP BY inntektsmelding_id) max_status
                                ON status.inntektsmelding_id = max_status.inntektsmelding_id
                                    AND status.opprettet = max_status.opprettet
                     INNER JOIN inntektsmelding im ON im.id = status.inntektsmelding_id
            WHERE status.status IN (:harStatus)    
            ORDER BY opprettet
            """,
            MapSqlParameterSource().addValue("harStatus", harStatus.asList(), Types.VARCHAR)
        ) { resultSet, _ ->
            resultSet.tilInntektsmelding()
        }
    }

    private fun ResultSet.tilInntektsmelding(): InntektsmeldingMedStatus {
        return InntektsmeldingMedStatus(
            id = getString("id"),
            fnr = getString("fnr"),
            orgNr = getString("org_nr"),
            orgNavn = getString("org_navn"),
            opprettet = getTimestamp("opprettet").toInstant(),
            vedtakFom = getDate("vedtak_fom").toLocalDate(),
            vedtakTom = getDate("vedtak_tom").toLocalDate(),
            eksternTimestamp = getTimestamp("ekstern_timestamp").toInstant(),
            eksternId = getString("ekstern_id"),
            status = StatusVerdi.valueOf(getString("status")),
            statusOpprettet = getTimestamp("status_opprettet").toInstant()
        )
    }

    private fun ResultSet.tilInntektsmeldingMedStatusHistorikk(): InntektsmeldingMedStatusHistorikk {
        val statusVerdier = if (this.getString("status") != null) {
            mutableListOf(mapInntektsmeldingStatus())
        } else {
            mutableListOf()
        }

        val inntektsmelding = InntektsmeldingMedStatusHistorikk(
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
            statusVerdier.add(mapInntektsmeldingStatus())
        }

        return inntektsmelding
    }

    private fun ResultSet.mapInntektsmeldingStatus() = StatusHistorikk(
        id = getString("status_id"),
        status = StatusVerdi.valueOf(getString("status")),
    )
}

data class InntektsmeldingMedStatus(
    val id: String,
    val fnr: String,
    val orgNr: String,
    val orgNavn: String,
    val opprettet: Instant,
    val vedtakFom: LocalDate,
    val vedtakTom: LocalDate,
    val eksternTimestamp: Instant,
    val eksternId: String,
    val status: StatusVerdi,
    val statusOpprettet: Instant,
)

data class InntektsmeldingMedStatusHistorikk(
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
    fun manglerBeskjedSendt() = statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_SENDT }
    fun manglerMeldingSendt() = statusHistorikk.any { it.status == StatusVerdi.DITT_SYKEFRAVAER_MANGLER_INNTEKTSMELDING_SENDT }
    fun manglerBeskjedDonet() = statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT }
    fun manglerMeldingDonet() = statusHistorikk.any { it.status == StatusVerdi.BRUKERNOTIFIKSJON_MANGLER_INNTEKTSMELDING_DONE_SENDT }
}

data class StatusHistorikk(
    val id: String,
    val status: StatusVerdi,
)
