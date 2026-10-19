package app.fuelwise.core.domain.privacy

/**
 * When the app lock asks again (PRD F-18): on every cold start, and when the app comes back after at least
 * [timeoutMinutes] in the background (0 = any time it left the screen).
 */
object AppLockPolicy {
    /** Choices offered in Settings, in minutes. */
    val TIMEOUT_CHOICES: List<Int> = listOf(0, 1, 5, 15)

    /**
     * Whether to lock on returning to the foreground. [backgroundedAtUtcMs] is null on a cold start, which always
     * locks when the lock is on.
     */
    fun shouldLock(enabled: Boolean, timeoutMinutes: Int, backgroundedAtUtcMs: Long?, nowUtcMs: Long): Boolean {
        if (!enabled) return false
        if (backgroundedAtUtcMs == null) return true
        val away = nowUtcMs - backgroundedAtUtcMs
        // A clock moved backwards counts as long enough: fail closed.
        return away < 0 || away >= timeoutMinutes.coerceAtLeast(0) * MS_PER_MINUTE
    }

    private const val MS_PER_MINUTE = 60_000L
}
