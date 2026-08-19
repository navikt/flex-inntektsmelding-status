package no.nav.helse.flex.vedtaksperiodebehandling

import java.time.OffsetDateTime

data class Behandlingstatusmelding(
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val tidspunkt: OffsetDateTime,
    val status: Behandlingstatustype,
    val eksterneSøknadIder: List<String>,
    val versjon: String = "2.0.0",
)

data class MeldingMedVersjon(
    val versjon: String? = null,
)

enum class Behandlingstatustype {
    OPPRETTET,
    VENTER_PÅ_ARBEIDSGIVER,
    VENTER_PÅ_SAKSBEHANDLER,
    VENTER_PÅ_ANNEN_PERIODE,
    FERDIG,
    BEHANDLES_UTENFOR_SPEIL,
}

fun Behandlingstatustype.tilSpleisStatus(): SpleisStatus =
    when (this) {
        Behandlingstatustype.OPPRETTET -> SpleisStatus.OPPRETTET
        Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER -> SpleisStatus.VENTER_PÅ_ARBEIDSGIVER
        Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER -> SpleisStatus.VENTER_PÅ_SAKSBEHANDLER
        Behandlingstatustype.FERDIG -> SpleisStatus.FERDIG
        Behandlingstatustype.BEHANDLES_UTENFOR_SPEIL -> SpleisStatus.BEHANDLES_UTENFOR_SPEIL
        Behandlingstatustype.VENTER_PÅ_ANNEN_PERIODE -> SpleisStatus.VENTER_PÅ_ANNEN_PERIODE
    }
