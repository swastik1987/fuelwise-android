package app.fuelwise.core.testing

import app.fuelwise.core.data.repository.LocationPrivacyRepository
import app.fuelwise.core.domain.trip.PrivatePlace
import app.fuelwise.core.domain.trip.RouteRetention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [LocationPrivacyRepository]; counts deletes instead of touching routes. */
class FakeLocationPrivacyRepository : LocationPrivacyRepository {
    override val retention = MutableStateFlow(RouteRetention.DEFAULT)
    override val privatePlaces = MutableStateFlow(emptyList<PrivatePlace>())
    var historyDeletes = 0

    override suspend fun setRetention(retention: RouteRetention) {
        this.retention.value = retention
    }

    override suspend fun addPrivatePlace(name: String, lat: Double, lng: Double) =
        privatePlaces.update { it + PrivatePlace("p${it.size + 1}", name, lat, lng) }

    override suspend fun removePrivatePlace(id: String) = privatePlaces.update { all -> all.filterNot { it.id == id } }

    override suspend fun pruneExpiredRoutes(nowUtcMs: Long): Int = 0

    override suspend fun deleteAllLocationHistory() {
        historyDeletes++
        privatePlaces.value = emptyList()
    }
}
