package app.fuelwise.core.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Values the user confirmed on the scan screen, handed to the Add fuel sheet (PRD F-15). Every field is optional:
 * an odometer scan carries only [odometerKm]. [photoToken] names the photo held in memory until the entry is saved.
 *
 * Feature modules can't see each other, so the hand-off is a string in the navigation arguments: [encode] / [decode].
 */
data class ScanPrefill(
    val total: Money? = null,
    val quantity: Double? = null,
    val unitPrice: Money? = null,
    val odometerKm: Long? = null,
    val dateTimeUtcMs: Long? = null,
    val stationName: String? = null,
    val photoToken: String? = null,
) {
    fun encode(): String = listOfNotNull(
        total?.let { "total" to it.paise.toString() },
        quantity?.let { "quantity" to it.toString() },
        unitPrice?.let { "price" to it.paise.toString() },
        odometerKm?.let { "odometer" to it.toString() },
        dateTimeUtcMs?.let { "date" to it.toString() },
        stationName?.let { "station" to it },
        photoToken?.let { "photo" to it },
    ).joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, Charsets.UTF_8.name()) }

    companion object {
        /** Unknown keys and unreadable values are skipped, so an older or damaged string still opens the sheet. */
        fun decode(text: String): ScanPrefill {
            val values = text.split('&').mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                if (key.isEmpty()) null else key to URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8.name())
            }.toMap()
            return ScanPrefill(
                total = values["total"]?.toLongOrNull()?.let(::Money),
                quantity = values["quantity"]?.toDoubleOrNull(),
                unitPrice = values["price"]?.toLongOrNull()?.let(::Money),
                odometerKm = values["odometer"]?.toLongOrNull(),
                dateTimeUtcMs = values["date"]?.toLongOrNull(),
                stationName = values["station"]?.takeIf { it.isNotBlank() },
                photoToken = values["photo"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}
