package app.fuelwise.core.domain.station

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StationsTest {
    private val hp = Station(
        osmId = 1001,
        lat = 12.97842,
        lng = 77.64081,
        brand = "HP",
        name = "Sri Lakshmi Fuels",
        fuels = setOf(StationFuel.Petrol, StationFuel.Diesel),
        charging = false,
    )

    @Test
    fun `lines round-trip and bad lines are skipped`() {
        val line = StationCodec.encodeLine(hp)
        assertThat(line).isEqualTo("1001\t12.97842\t77.64081\t3\tHP\tSri Lakshmi Fuels")
        assertThat(StationCodec.decodeLine(line)).isEqualTo(hp)
        val ev = hp.copy(osmId = 7, fuels = emptySet(), charging = true, brand = null, name = "Tab\tname")
        assertThat(StationCodec.decodeLine(StationCodec.encodeLine(ev))).isEqualTo(ev.copy(name = "Tab name"))
        assertThat(StationCodec.decodeLine("${StationCodec.HEADER_PREFIX}\t2026-10-23")).isNull()
        assertThat(StationCodec.decodeLine("garbage")).isNull()
        assertThat(StationCodec.decodeLine("x\t1\t2\t3\t\t")).isNull()
    }

    @Test
    fun `nearest stations within a radius, across grid cells`() {
        // Across the road (~35 m) and one in the next 0.01° cell north (~187 m), plus one 2 km away.
        val across = hp.copy(osmId = 2, lat = 12.97870, lng = 77.64095, brand = "IndianOil")
        val nextCell = hp.copy(osmId = 3, lat = 12.98010, lng = 77.64081, brand = "BPCL")
        val far = hp.copy(osmId = 4, lat = 12.99600, lng = 77.64081)
        val index = StationIndex(listOf(far, nextCell, across, hp))
        val near = index.near(12.97842, 77.64081, radiusM = 200.0)
        assertThat(near.map { it.first.osmId }).containsExactly(1001L, 2L, 3L).inOrder()
        assertThat(near[1].second).isWithin(5.0).of(34.0)
        assertThat(index.near(12.97842, 77.64081, radiusM = 40.0).map { it.first.osmId }).containsExactly(1001L, 2L)
    }

    @Test
    fun `brands are canonical and fuel fit reads the right tag`() {
        assertThat(canonicalBrand("Indian Oil Corporation Ltd", null)).isEqualTo("IndianOil")
        assertThat(canonicalBrand(null, "Hindustan Petroleum")).isEqualTo("HP")
        assertThat(canonicalBrand("Bharat Petroleum")).isEqualTo("BPCL")
        assertThat(canonicalBrand("Sri Lakshmi Fuels")).isNull()
        assertThat(hp.label).isEqualTo("HP · Sri Lakshmi Fuels")
        assertThat(hp.sells(StationFuel.Petrol)).isTrue()
        assertThat(hp.sells(StationFuel.Cng)).isFalse()
        assertThat(hp.copy(charging = true).sells(StationFuel.Electric)).isTrue()
    }
}
