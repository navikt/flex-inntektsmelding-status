package no.nav.helse.flex.util

import java.util.*
import kotlin.random.Random

class SeededUuid(
    seedString: String,
    intitialSkips: Int = 0,
) {
    val randomGenerator: Random

    init {
        val seed = UUID.fromString(seedString)
        randomGenerator = Random(seed.mostSignificantBits xor seed.leastSignificantBits)
        repeat(intitialSkips) {
            nextUUID()
        }
    }

    fun nextUUID(): String {
        val mostSigBits = randomGenerator.nextLong()
        val leastSigBits = randomGenerator.nextLong()
        return UUID(mostSigBits, leastSigBits).toString()
    }
}
