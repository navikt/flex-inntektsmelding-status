package no.nav.helse.flex.varselutsending

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.helse.flex.varselutsending.DryRunSjekk")

fun List<CronJobStatus>.dryRunSjekk(
    grense: Int,
    status: CronJobStatus,
) {
    val antallUtsendinger =

        this.filter { it == status }

    log.info("Dry run fant ${antallUtsendinger.size} varsler av type $status")

    if (antallUtsendinger.size >= grense) {
        val melding =
            "Funksjonell grense for antall  $status varsler nådd, antall varsler: ${antallUtsendinger.size}. " +
                "Grensen er satt til $grense"
        log.error(melding)
        throw RuntimeException(melding)
    }
}
