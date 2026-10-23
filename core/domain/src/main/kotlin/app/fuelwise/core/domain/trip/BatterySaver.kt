package app.fuelwise.core.domain.trip

import java.util.Locale

// Phone-maker battery savers (PRD F-21.7): many phones sold in India stop background apps, which kills automatic
// trips. Pure rules: which family of phone this is, and whether trips look blocked.

/** Phone families whose battery savers need their own steps. [Other] gets Android's standard screen. */
enum class PhoneBrand { Xiaomi, Samsung, Oppo, Vivo, Huawei, Other }

/**
 * From `Build.MANUFACTURER` (and `Build.BRAND` as a fallback): Redmi and POCO are Xiaomi; realme and OnePlus use
 * ColorOS.
 */
fun phoneBrand(manufacturer: String, brand: String = ""): PhoneBrand {
    val names = listOf(manufacturer, brand).map { it.lowercase(Locale.ROOT).trim() }
    fun any(vararg keys: String) = names.any { name -> keys.any { name.startsWith(it) } }
    return when {
        any("xiaomi", "redmi", "poco") -> PhoneBrand.Xiaomi
        any("samsung") -> PhoneBrand.Samsung
        any("oppo", "realme", "oneplus") -> PhoneBrand.Oppo
        any("vivo", "iqoo") -> PhoneBrand.Vivo
        any("huawei", "honor") -> PhoneBrand.Huawei
        else -> PhoneBrand.Other
    }
}

/**
 * The health check (PRD F-21.7): automatic trips are probably being stopped when activity detection has been silent
 * for [SILENT_DAYS] while the user logged a fill-up in that time, so they were clearly driving. Silence counts from
 * the last detection event, or from when automatic trips were turned on if there hasn't been one.
 */
fun autoTripsLikelyBlocked(
    enabledAtUtcMs: Long?,
    lastActivityEventUtcMs: Long?,
    fillUpTimesUtcMs: List<Long>,
    nowUtcMs: Long,
): Boolean {
    val since = lastActivityEventUtcMs ?: enabledAtUtcMs ?: return false
    if (nowUtcMs - since < SILENT_DAYS * MS_PER_DAY) return false
    return fillUpTimesUtcMs.any { it > since && it <= nowUtcMs }
}

const val SILENT_DAYS = 3L

private const val MS_PER_DAY = 24L * 60 * 60 * 1_000
