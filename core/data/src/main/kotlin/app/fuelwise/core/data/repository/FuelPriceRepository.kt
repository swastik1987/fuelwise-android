package app.fuelwise.core.data.repository

import app.fuelwise.core.database.dao.FuelPriceDao
import app.fuelwise.core.database.model.FuelPriceEntity
import app.fuelwise.core.domain.id.IdGenerator
import app.fuelwise.core.domain.prices.FeedPrice
import app.fuelwise.core.model.FuelPrice
import app.fuelwise.core.model.FuelPriceOrigin
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PriceFuel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** City fuel prices (PRD F-09): typed by the user, or written by the price feed. */
interface FuelPriceRepository {
    /** Prices for [cityIds] from [since] on, oldest first. */
    fun observePrices(cityIds: Set<String>, since: LocalDate): Flow<List<FuelPrice>>

    /** One city and fuel between two days, inclusive, oldest first. */
    suspend fun history(cityId: String, fuel: PriceFuel, from: LocalDate, to: LocalDate): List<FuelPrice>

    /** The newest price for a city and fuel on or before [date]. */
    suspend fun latestOnOrBefore(cityId: String, fuel: PriceFuel, date: LocalDate): FuelPrice?

    /** Sets the user's price for a day, replacing any price (typed or feed) already there. Returns the row id. */
    suspend fun saveManual(cityId: String, fuel: PriceFuel, date: LocalDate, price: Money): String

    suspend fun delete(id: String)

    /**
     * Writes feed prices. A day the user typed a price for keeps the user's price; unchanged prices aren't rewritten.
     * Returns how many rows changed.
     */
    suspend fun applyFeed(prices: List<FeedPrice>): Int
}

internal class OfflineFuelPriceRepository @Inject constructor(
    private val dao: FuelPriceDao,
    private val ids: IdGenerator,
    private val sync: SyncStamper,
) : FuelPriceRepository {
    override fun observePrices(cityIds: Set<String>, since: LocalDate): Flow<List<FuelPrice>> =
        dao.observe(cityIds.toList(), since.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    override suspend fun history(cityId: String, fuel: PriceFuel, from: LocalDate, to: LocalDate): List<FuelPrice> =
        dao.between(cityId, fuel.name, from.toEpochDay(), to.toEpochDay()).map { it.toModel() }

    override suspend fun latestOnOrBefore(cityId: String, fuel: PriceFuel, date: LocalDate): FuelPrice? =
        dao.latestOnOrBefore(cityId, fuel.name, date.toEpochDay())?.toModel()

    override suspend fun saveManual(cityId: String, fuel: PriceFuel, date: LocalDate, price: Money): String {
        val existing = dao.find(cityId, fuel.name, date.toEpochDay())
        val id = existing?.id ?: ids.newId()
        dao.upsert(
            FuelPriceEntity(
                id = id,
                cityId = cityId,
                fuel = fuel.name,
                epochDay = date.toEpochDay(),
                pricePaise = price.paise,
                origin = FuelPriceOrigin.Manual.name,
                // A re-added price revives the row the delete tombstoned; the key (city, fuel, day) is unique.
                sync = sync.stamp(existing?.sync?.createdAt),
            ),
        )
        return id
    }

    override suspend fun delete(id: String) = dao.tombstone(id, sync.now(), sync.device())

    override suspend fun applyFeed(prices: List<FeedPrice>): Int {
        val changed = prices.mapNotNull { p ->
            val existing = dao.find(p.cityId, p.fuel.name, p.date.toEpochDay())
            val live = existing?.takeIf { it.sync.deletedAt == null }
            when {
                live?.origin == FuelPriceOrigin.Manual.name -> null

                live != null && live.pricePaise == p.price.paise -> null

                else -> FuelPriceEntity(
                    id = existing?.id ?: ids.newId(),
                    cityId = p.cityId,
                    fuel = p.fuel.name,
                    epochDay = p.date.toEpochDay(),
                    pricePaise = p.price.paise,
                    origin = FuelPriceOrigin.Feed.name,
                    sync = sync.stamp(existing?.sync?.createdAt),
                )
            }
        }
        if (changed.isNotEmpty()) dao.upsertAll(changed)
        return changed.size
    }
}

internal fun FuelPriceEntity.toModel() = FuelPrice(
    id = id,
    cityId = cityId,
    fuel = PriceFuel.valueOf(fuel),
    date = LocalDate.ofEpochDay(epochDay),
    price = Money(pricePaise),
    origin = FuelPriceOrigin.valueOf(origin),
    updatedAtUtcMs = sync.updatedAt,
)
