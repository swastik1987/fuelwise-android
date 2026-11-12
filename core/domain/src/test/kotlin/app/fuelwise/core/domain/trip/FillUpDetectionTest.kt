package app.fuelwise.core.domain.trip

import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationFuel
import app.fuelwise.core.model.TrackPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FillUpDetectionTest {
    private val minute = 60_000L

    // 0.0001° ≈ 11 m of latitude.
    private fun fix(atMin: Double, lat: Double) = TrackPoint((atMin * minute).toLong(), lat, 77.64, 8f, null, 0)

    @Test
    fun `a 6-minute stop mid-trip and the trip end are found, a red light is not`() {
        val points = listOf(
            fix(0.0, 12.9000),
            fix(1.0, 12.9050),
            // Red light: 40 s.
            fix(1.5, 12.9100),
            fix(2.2, 12.9150),
            // At the pump from minute 4, gone at minute 10 (no fixes while still).
            fix(4.0, 12.9200),
            fix(4.5, 12.9201),
            fix(10.0, 12.9250),
            fix(12.0, 12.9300),
        )
        val stops = findStops(points)
        assertThat(stops.map { it.lat }).containsExactly(12.9200, 12.9300).inOrder()
        assertThat(stops[0].durationMs).isEqualTo(6 * minute)
        // The end has no known length.
        assertThat(stops[1].durationMs).isNull()
    }

    @Test
    fun `too long a stop is parking, not a fill-up`() {
        val stops = findStops(listOf(fix(0.0, 12.9000), fix(40.0, 12.9001), fix(45.0, 12.9100), fix(46.0, 12.9200)))
        assertThat(stops.map { it.startUtcMs }).containsExactly(46 * minute)
    }

    private fun station(id: Long, charging: Boolean = false, cng: Boolean = false) = Station(
        osmId = id,
        lat = 12.92,
        lng = 77.64,
        brand = "HP",
        name = null,
        fuels = if (cng) setOf(StationFuel.Petrol, StationFuel.Cng) else setOf(StationFuel.Petrol),
        charging = charging,
    )

    @Test
    fun `the station that fits the vehicle, unless rejected three times`() {
        val petrol = station(1)
        val cng = station(2, cng = true)
        val charger = station(3, charging = true)
        val near = listOf(petrol to 12.0, cng to 30.0, charger to 20.0, station(4) to 60.0)
        assertThat(stationForStop(near, FillNeed.Liquid, emptyMap())).isEqualTo(petrol)
        assertThat(stationForStop(near, FillNeed.Cng, emptyMap())).isEqualTo(cng)
        assertThat(stationForStop(near, FillNeed.Electric, emptyMap())).isEqualTo(charger)
        // "Not a fill-up" three times at the nearest: the next one in reach.
        assertThat(stationForStop(near, FillNeed.Liquid, mapOf(1L to 3))).isEqualTo(cng)
        assertThat(stationForStop(near, FillNeed.Liquid, mapOf(1L to 2))).isEqualTo(petrol)
        // Beyond 40 m doesn't count.
        assertThat(stationForStop(listOf(station(4) to 60.0), FillNeed.Liquid, emptyMap())).isNull()
    }

    @Test
    fun `no prompt when already logged or prompted recently`() {
        val stop = 100 * 60 * minute
        assertThat(shouldPromptFillUp(stop, emptyList(), null, stop)).isTrue()
        // Logged 90 minutes later from the receipt: already done.
        assertThat(shouldPromptFillUp(stop, listOf(stop + 90 * minute), null, stop)).isFalse()
        assertThat(shouldPromptFillUp(stop, listOf(stop - 3 * 60 * minute), null, stop)).isTrue()
        // Prompted 4 hours ago: at most one per 6 hours.
        assertThat(shouldPromptFillUp(stop, emptyList(), stop - 4 * 60 * minute, stop)).isFalse()
        assertThat(shouldPromptFillUp(stop, emptyList(), stop - 7 * 60 * minute, stop)).isTrue()
    }

    @Test
    fun `the prefill survives the notification tap`() {
        val p = FillUpPrefill("safari", 1_729_000_000_000, "HP 	 Indiranagar")
        assertThat(FillUpPrefill.decode(p.encode())).isEqualTo(p.copy(station = "HP   Indiranagar"))
        assertThat(FillUpPrefill.decode(FillUpPrefill("ev", 1, null).encode())?.station).isNull()
        assertThat(FillUpPrefill.decode("junk")).isNull()
        assertThat(FillUpPrefill.decode(null)).isNull()
        // The station's position travels too; a version-1 prompt from before an update still opens.
        val placed = FillUpPrefill("safari", 5, "HP", 12.9716, 77.6408)
        assertThat(FillUpPrefill.decode(placed.encode())).isEqualTo(placed)
        assertThat(FillUpPrefill.decode("1\tsafari\t5\tHP")).isEqualTo(FillUpPrefill("safari", 5, "HP"))
    }
}
