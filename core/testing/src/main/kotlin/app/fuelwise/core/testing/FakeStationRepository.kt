package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.StationRepository
import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationIndex

/** A [StationRepository] over a few stations, matched the same way as the bundled list. */
class FakeStationRepository(stations: List<Station> = emptyList()) : StationRepository {
    private val index = StationIndex(stations)

    override suspend fun near(lat: Double, lng: Double, radiusM: Double) = index.near(lat, lng, radiusM)

    override suspend fun count(): Int = index.size
}
