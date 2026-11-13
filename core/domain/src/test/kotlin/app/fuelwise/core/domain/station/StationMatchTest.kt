package app.fuelwise.core.domain.station

import app.fuelwise.core.domain.trip.FillNeed
import app.fuelwise.core.domain.trip.distanceM
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Refuel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** PRD F-21.1 ranking. Positions around 100 Feet Road, Indiranagar, Bengaluru; stations are made up. */
class StationMatchTest {
    private val hereLat = 12.97842
    private val hereLng = 77.64081

    private fun station(
        id: Long,
        dLat: Double,
        brand: String?,
        name: String?,
        fuels: Set<StationFuel>,
        ev: Boolean = false,
    ) = Station(id, hereLat + dLat, hereLng, brand, name, fuels, ev)

    private val hp = station(1, 0.00030, "HP", "Sri Lakshmi Fuels", setOf(StationFuel.Petrol, StationFuel.Diesel))
    private val iocl = station(2, -0.00060, "IndianOil", null, setOf(StationFuel.Petrol, StationFuel.Cng))
    private val bpcl = station(3, 0.00110, "BPCL", "Speed Point", emptySet())
    private val far = station(4, 0.00200, "Nayara", null, emptySet())
    private val charger = station(5, 0.00010, null, "Statiq Hub", emptySet(), ev = true)
    private val unnamed = station(6, 0.00005, null, null, setOf(StationFuel.Petrol))

    private fun listed(vararg stations: Station) =
        stations.map { it to distanceM(hereLat, hereLng, it.lat, it.lng) }.filter { it.second <= StationMatch.WIDE_M }

    private fun fill(name: String?, lat: Double?, lng: Double? = hereLng) = Refuel(
        id = "r-$name-$lat",
        vehicleId = "v",
        tankId = "t",
        dateTimeUtcMs = 0,
        zoneId = "Asia/Kolkata",
        odometerKm = 1.0,
        quantity = 20.0,
        unitPrice = Money(10_292),
        total = Money(205_840),
        isFull = true,
        stationName = name,
        lat = lat,
        lng = lng,
    )

    @Test
    fun `nearest named stations first, up to three, unnamed and charging-only left out`() {
        val options = StationMatch.options(
            hereLat,
            hereLng,
            listed(bpcl, iocl, hp, charger, unnamed),
            emptyList(),
            FillNeed.Liquid,
        )
        assertThat(
            options.map {
                it.label
            },
        ).containsExactly("HP · Sri Lakshmi Fuels", "IndianOil", "BPCL · Speed Point")
            .inOrder()
        assertThat(options.first().distanceM).isWithin(2.0).of(33.0)
        assertThat(options.first().usedBefore).isFalse()
    }

    @Test
    fun `widens to 300 m only when nothing is within 150 m`() {
        val options = StationMatch.options(hereLat, hereLng, listed(far), emptyList(), FillNeed.Liquid)
        assertThat(options.single().label).isEqualTo("Nayara")
        assertThat(StationMatch.options(hereLat, hereLng, emptyList(), emptyList(), FillNeed.Liquid)).isEmpty()
    }

    @Test
    fun `a station used before beats a nearer one, under the user's own name`() {
        // The user logged the pump across the road as "IOCL 100 Ft Rd" at its spot before.
        val history = listOf(fill("IOCL 100 Ft Rd", iocl.lat), fill("IOCL 100 Ft Rd", iocl.lat + 0.00001))
        val options = StationMatch.options(hereLat, hereLng, listed(hp, iocl), history, FillNeed.Liquid)
        assertThat(options.map { it.label }).containsExactly("IOCL 100 Ft Rd", "HP · Sri Lakshmi Fuels").inOrder()
        assertThat(options.first().usedBefore).isTrue()
    }

    @Test
    fun `history without a name or coordinates, or far away, is ignored`() {
        val history = listOf(fill(null, hereLat), fill("Home pump", null, null), fill("Old pump", hereLat + 0.01))
        val options = StationMatch.options(hereLat, hereLng, listed(hp), history, FillNeed.Liquid)
        assertThat(options.map { it.label }).containsExactly("HP · Sri Lakshmi Fuels")
    }

    @Test
    fun `a CNG vehicle prefers a pump tagged with CNG`() {
        val options = StationMatch.options(hereLat, hereLng, listed(hp, iocl), emptyList(), FillNeed.Cng)
        assertThat(options.first().label).isEqualTo("IndianOil")
    }

    @Test
    fun `an EV gets charging points only`() {
        val options = StationMatch.options(hereLat, hereLng, listed(hp, charger, iocl), emptyList(), FillNeed.Electric)
        assertThat(options.map { it.label }).containsExactly("Statiq Hub")
    }

    @Test
    fun `back-dated means more than 30 minutes before now`() {
        val now = 1_000_000_000L
        assertThat(isBackDated(now - 30 * 60_000L, now)).isFalse()
        assertThat(isBackDated(now - 31 * 60_000L, now)).isTrue()
    }
}
