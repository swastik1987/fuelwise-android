package app.fuelwise.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.fuelwise.core.database.model.FuelPriceEntity
import kotlinx.coroutines.flow.Flow

/** City fuel prices (PRD F-09). Deletes are tombstones (CLAUDE.md rule 3). */
@Dao
interface FuelPriceDao {
    /** Live prices for [cityIds] from [fromEpochDay] on, oldest first. */
    @Query(
        """
        SELECT * FROM fuel_price
        WHERE cityId IN (:cityIds) AND epochDay >= :fromEpochDay AND deletedAt IS NULL
        ORDER BY epochDay
        """,
    )
    fun observe(cityIds: List<String>, fromEpochDay: Long): Flow<List<FuelPriceEntity>>

    /** One city and fuel between two days, inclusive. */
    @Query(
        """
        SELECT * FROM fuel_price
        WHERE cityId = :cityId AND fuel = :fuel AND epochDay BETWEEN :fromEpochDay AND :toEpochDay
            AND deletedAt IS NULL
        ORDER BY epochDay
        """,
    )
    suspend fun between(cityId: String, fuel: String, fromEpochDay: Long, toEpochDay: Long): List<FuelPriceEntity>

    /** The newest live price for a city and fuel on or before a day. */
    @Query(
        """
        SELECT * FROM fuel_price
        WHERE cityId = :cityId AND fuel = :fuel AND epochDay <= :epochDay AND deletedAt IS NULL
        ORDER BY epochDay DESC LIMIT 1
        """,
    )
    suspend fun latestOnOrBefore(cityId: String, fuel: String, epochDay: Long): FuelPriceEntity?

    /** The row for a day, tombstone included, so a re-added price reuses it (the key is unique). */
    @Query("SELECT * FROM fuel_price WHERE cityId = :cityId AND fuel = :fuel AND epochDay = :epochDay")
    suspend fun find(cityId: String, fuel: String, epochDay: Long): FuelPriceEntity?

    @Query("SELECT * FROM fuel_price WHERE id = :id")
    suspend fun byId(id: String): FuelPriceEntity?

    @Upsert
    suspend fun upsert(price: FuelPriceEntity)

    /** One transaction, so readers never see half a feed run. */
    @Upsert
    suspend fun upsertAll(prices: List<FuelPriceEntity>)

    @Query(
        """
        UPDATE fuel_price SET deletedAt = :at, updatedAt = :at, deviceId = :deviceId
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun tombstone(id: String, at: Long, deviceId: String)
}
