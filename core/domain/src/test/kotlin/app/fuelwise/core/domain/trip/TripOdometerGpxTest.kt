package app.fuelwise.core.domain.trip

import app.fuelwise.core.domain.engine.OdometerReading
import app.fuelwise.core.domain.entry.odometerReadings
import app.fuelwise.core.domain.entry.tripEndOdometerHint
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Tank
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.model.Trip
import app.fuelwise.core.model.TripPurpose
import app.fuelwise.core.model.TripSource
import app.fuelwise.core.model.TripStatus
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.model.VehicleType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class TripOdometerGpxTest {
    private val safari = Vehicle(
        id = "safari",
        name = "Safari",
        type = VehicleType.Car,
        tanks = listOf(Tank("diesel", EnergyType.DIESEL, 50.0, isPrimary = true)),
        startOdometerKm = 42_318.0,
        startOdometerAtUtcMs = 0L,
    )
    private val gps = Trip(
        id = "t1",
        vehicleId = "safari",
        startUtcMs = 1_000_000,
        endUtcMs = 2_000_000,
        zoneId = "Asia/Kolkata",
        distanceKm = 23.4,
        purpose = TripPurpose.Commute,
        source = TripSource.Gps,
    )

    @Test
    fun `finished trips feed odometer readings, others don't`() {
        val readings = odometerReadings(
            safari,
            emptyList(),
            emptyList(),
            listOf(
                gps.copy(startOdometerKm = 44_000.0, endOdometerKm = 44_023.4),
                gps.copy(id = "rec", status = TripStatus.Recording, endOdometerKm = 50_000.0),
                gps.copy(id = "other", vehicleId = "ertiga", endOdometerKm = 60_000.0),
            ),
        )
        assertThat(readings.map { it.odometerKm }).containsExactly(42_318.0, 44_000.0, 44_023.4)
        assertThat(readings.last().atUtcMs).isEqualTo(2_000_000)
    }

    @Test
    fun `end odometer hint is the last reading before the trip plus its distance`() {
        val readings = listOf(OdometerReading(43_900.0, 500_000), OdometerReading(44_100.0, 3_000_000))
        assertThat(tripEndOdometerHint(gps, readings)!!).isWithin(1e-9).of(43_923.4)
        assertThat(tripEndOdometerHint(gps.copy(startOdometerKm = 44_000.0), readings)!!).isWithin(1e-9).of(44_023.4)
        assertThat(tripEndOdometerHint(gps, emptyList())).isNull()
    }

    @Test
    fun `typed end odometer works back the start`() {
        val updated = gps.withEndOdometer(44_023.4)!!
        assertThat(updated.endOdometerKm).isEqualTo(44_023.4)
        assertThat(updated.startOdometerKm!!).isWithin(1e-9).of(44_000.0)
        assertThat(gps.copy(startOdometerKm = 44_000.0).withEndOdometer(43_999.0)).isNull()
        assertThat(gps.withEndOdometer(0.0)).isNull()
        // Below the last reading known before the trip.
        assertThat(gps.withEndOdometer(100.0, floorKm = 43_900.0)).isNull()
        // Less than the distance driven: no start can be worked out, the end is still kept.
        assertThat(gps.withEndOdometer(10.0)?.startOdometerKm).isNull()
    }

    @Test
    fun `GPX has one track per trip and one segment per stretch`() {
        val points = listOf(
            TrackPoint(0, 12.9352, 77.6245, 8f, null, 0),
            TrackPoint(5_000, 12.93, 77.629, 8f, null, 0),
            TrackPoint(600_000, 12.928, 77.638, 8f, null, 1),
        )
        val gpx = GpxWriter.write(listOf(GpxTrack("HSR <Layout> & co", points), GpxTrack("Empty", emptyList())))
        assertThat(gpx).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\"")
        assertThat(gpx).contains("xmlns=\"http://www.topografix.com/GPX/1/1\"")
        assertThat(gpx).contains("<name>HSR &lt;Layout&gt; &amp; co</name>")
        assertThat(gpx).doesNotContain("Empty")
        assertThat(Regex("<trk>").findAll(gpx).count()).isEqualTo(1)
        assertThat(Regex("<trkseg>").findAll(gpx).count()).isEqualTo(2)
        assertThat(gpx)
            .contains("<trkpt lat=\"12.9352000\" lon=\"77.6245000\"><time>1970-01-01T00:00:00Z</time></trkpt>")
        assertThat(gpx).endsWith("</gpx>\n")
    }

    @Test
    fun `range export picks finished GPS trips in the period, oldest first`() {
        val zone = ZoneId.of("Asia/Kolkata")
        // Monday 19 Oct 2026, 10:00 IST.
        val now = LocalDate.of(2026, 10, 19).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        fun at(m: Int, d: Int) = LocalDate.of(2026, m, d).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val trips = listOf(
            gps.copy(id = "today", startUtcMs = at(10, 19)),
            gps.copy(id = "sunday", startUtcMs = at(10, 18)),
            gps.copy(id = "august", startUtcMs = at(8, 1)),
            gps.copy(id = "manual", startUtcMs = at(10, 19), source = TripSource.Manual),
            gps.copy(id = "recording", startUtcMs = at(10, 19), status = TripStatus.Recording),
        )
        assertThat(tripsToExport(trips, ExportRange.ThisWeek, now, zone).map { it.id }).containsExactly("today")
        assertThat(tripsToExport(trips, ExportRange.ThisMonth, now, zone).map { it.id })
            .containsExactly("sunday", "today").inOrder()
        assertThat(tripsToExport(trips, ExportRange.LastThreeMonths, now, zone).map { it.id })
            .containsExactly("august", "sunday", "today").inOrder()
        assertThat(tripsToExport(trips, ExportRange.All, now, zone)).hasSize(3)

        assertThat(gpxTrackName(gps.copy(fromPlace = "HSR Layout", toPlace = "Whitefield"), zone))
            .isEqualTo("HSR Layout → Whitefield")
        assertThat(gpxTrackName(gps.copy(startUtcMs = at(10, 16)), zone)).isEqualTo("Trip 2026-10-16 08:00")
    }
}
