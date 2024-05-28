package no.nav.helse.flex.vedtaksperiodebehandling

import java.time.OffsetDateTime

data class Behandlingstatusmelding(
    val vedtaksperiodeId: String,
    val behandlingId: String,
    val tidspunkt: OffsetDateTime,
    val status: Behandlingstatustype,
    val eksterneSøknadIder: List<String>,
    //  val eksternSøknadId: String? = null,
) {
    val versjon = "1.0.0-beta"
}

enum class Behandlingstatustype {
    OPPRETTET,
    VENTER_PÅ_ARBEIDSGIVER,
    VENTER_PÅ_SAKSBEHANDLER,
    FERDIG,
    BEHANDLES_UTENFOR_SPEIL,
}

fun Behandlingstatustype.tilStatusVerdi(): StatusVerdi {
    return when (this) {
        Behandlingstatustype.OPPRETTET -> StatusVerdi.OPPRETTET
        Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER -> StatusVerdi.VENTER_PÅ_ARBEIDSGIVER
        Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER -> StatusVerdi.VENTER_PÅ_SAKSBEHANDLER
        Behandlingstatustype.FERDIG -> StatusVerdi.FERDIG
        Behandlingstatustype.BEHANDLES_UTENFOR_SPEIL -> StatusVerdi.BEHANDLES_UTENFOR_SPEIL
    }
}
