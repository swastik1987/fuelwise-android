package app.fuelwise.core.domain.scan

/**
 * Picks one reading from several reads of the same photo (PRD F-15 "Snap": the plain image and its clean-ups are each
 * read). A reading whose values check out wins, then the one with the most values; ties go to the earlier one.
 */
object ScanChoice {
    fun <T> best(readings: List<T>, valid: (T) -> Boolean, fieldCount: (T) -> Int): T? = readings.withIndex()
        .filter { fieldCount(it.value) > 0 }
        .maxWithOrNull(
            compareBy<IndexedValue<T>> { valid(it.value) }
                .thenBy { fieldCount(it.value) }
                .thenByDescending { it.index },
        )?.value

    /**
     * Every read plus all their lines together: one clean-up may read the amount and another the rate. The combined
     * read goes last, so a single read that checks out by itself is preferred.
     */
    fun candidates(reads: List<List<OcrLine>>): List<List<OcrLine>> =
        if (reads.size > 1) reads + listOf(reads.flatten()) else reads
}
