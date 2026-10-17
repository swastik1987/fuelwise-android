package app.fuelwise.core.domain.id

import java.security.SecureRandom
import java.time.Clock
import javax.inject.Inject

/** Produces IDs for syncable entities (CLAUDE.md rule 3). */
fun interface IdGenerator {
    fun newId(): String
}

/**
 * UUID version 7 (RFC 9562): 48-bit Unix-ms timestamp, then random bits. Lexicographic order follows creation time,
 * which keeps Room indexes and Drive change-sets append-friendly. IDs from the same millisecond stay increasing.
 */
class UuidV7Generator @Inject constructor(
    private val clock: Clock,
) : IdGenerator {
    private val random = SecureRandom()
    private val lock = Any()
    private var lastMillis = -1L
    private var counter = 0

    override fun newId(): String {
        val (millis, seq) = synchronized(lock) {
            val now = clock.millis()
            if (now > lastMillis) {
                lastMillis = now
                counter = random.nextInt(COUNTER_SEED_BOUND)
            } else {
                counter++
                if (counter > COUNTER_MAX) {
                    // Counter exhausted within one millisecond (or the clock went back): borrow the next millisecond.
                    lastMillis++
                    counter = 0
                }
            }
            lastMillis to counter
        }
        val msb = (millis shl TIMESTAMP_SHIFT) or (VERSION shl VERSION_SHIFT) or seq.toLong()
        val lsb = (random.nextLong() and VARIANT_CLEAR_MASK) or VARIANT_BITS
        return format(msb, lsb)
    }

    private fun format(msb: Long, lsb: Long): String = buildString(UUID_LENGTH) {
        appendHex(msb ushr 32, 8)
        append('-')
        appendHex(msb ushr 16, 4)
        append('-')
        appendHex(msb, 4)
        append('-')
        appendHex(lsb ushr 48, 4)
        append('-')
        appendHex(lsb, 12)
    }

    private fun StringBuilder.appendHex(value: Long, digits: Int) {
        for (i in digits - 1 downTo 0) {
            append(HEX[((value ushr (i * 4)) and 0xF).toInt()])
        }
    }

    private companion object {
        const val TIMESTAMP_SHIFT = 16
        const val VERSION = 0x7L
        const val VERSION_SHIFT = 12
        const val COUNTER_MAX = 0xFFF
        const val COUNTER_SEED_BOUND = 0x800 // leave headroom for increments within one millisecond
        const val VARIANT_CLEAR_MASK = 0x3FFF_FFFF_FFFF_FFFFL
        const val VARIANT_BITS = Long.MIN_VALUE // binary 10 in the top two bits
        const val UUID_LENGTH = 36
        const val HEX = "0123456789abcdef"
    }
}
