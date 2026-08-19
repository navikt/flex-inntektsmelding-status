package no.nav.helse.flex.util

import no.nav.helse.flex.vedtaksperiodebehandling.VarslingStatus
import java.util.*
import kotlin.random.Random

class SeededUuid(
    uuid: String,
    varselstatus: VarslingStatus,
    antall: Int = 0,
) {
    val randomGenerator: Random

    init {
        val navn = "$uuid|${varselstatus.name}|$antall"
        val seed = UUID.nameUUIDFromBytes(navn.toByteArray())
        randomGenerator = Random(seed.mostSignificantBits xor seed.leastSignificantBits)
    }

    fun nextUUID(): String {
        val mostSigBits = randomGenerator.nextLong()
        val leastSigBits = randomGenerator.nextLong()
        return UUID(mostSigBits, leastSigBits).toString()
    }
}
