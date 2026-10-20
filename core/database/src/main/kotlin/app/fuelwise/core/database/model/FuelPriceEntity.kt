package app.fuelwise.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * City fuel prices (PRD F-09.1), typed by the user or from the price feed (F-09.2). One row per city, fuel and day:
 * a typed price replaces the feed's for that day, and the feed never overwrites a typed one. Syncable like user
 * data (CLAUDE.md rule 3), since typed prices are the user's own records.
 */
@Entity(tableName = "fuel_price", indices = [Index(value = ["cityId", "fuel", "epochDay"], unique = true)])
data class FuelPriceEntity(
    @PrimaryKey val id: String,
    val cityId: String,
    /** PriceFuel name. */
    val fuel: String,
    /** The day the price applies, as LocalDate.toEpochDay(). Prices are set per calendar day in India. */
    val epochDay: Long,
    val pricePaise: Long,
    /** FuelPriceOrigin name. */
    val origin: String,
    @Embedded val sync: SyncMeta,
)
