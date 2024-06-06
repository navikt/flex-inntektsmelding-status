package no.nav.helse.flex.util

import no.nav.helse.flex.varselutsending.CronJobStatus

fun MutableMap<CronJobStatus, Int>.increment(status: CronJobStatus) {
    if (this.containsKey(status)) {
        this[status] = this[status]!! + 1
    } else {
        this[status] = 1
    }
}
