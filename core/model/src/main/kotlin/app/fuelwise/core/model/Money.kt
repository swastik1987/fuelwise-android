package app.fuelwise.core.model

/**
 * An amount of money in paise (1 ₹ = 100 paise). Money is never a floating-point number (CLAUDE.md rule 1).
 * Format for display with `core:ui` formatters only.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(Math.addExact(paise, other.paise))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(paise, other.paise))

    operator fun unaryMinus(): Money = Money(Math.negateExact(paise))

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    val isNegative: Boolean get() = paise < 0

    companion object {
        val ZERO = Money(0)

        fun ofRupees(rupees: Long): Money = Money(Math.multiplyExact(rupees, PAISE_PER_RUPEE))

        const val PAISE_PER_RUPEE = 100L
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }
