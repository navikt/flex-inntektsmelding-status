package no.nav.helse.flex.melding

import org.springframework.stereotype.Repository

@Repository
class MeldingRepository

enum class MeldingStatus {
    MANGLER,
    MOTTATT,
    BRUKERNOTIFIKSJON_SENDT,
    DITT_SYKEFRAVAER_MELDING_SENDT,
    BRUKERNOTIFIKSJON_LUKKET,
    DITT_SYKEFRAVAER_LUKKET,
    BRUKERNOTIFIKSJON_DONE_SENDT,
    DITT_SYKEFRAVAER_DONE_SENDT,
}
