package app.fuelwise.feature.insights

import app.fuelwise.core.data.repository.CategoryRepository
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.UserDataRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.insights.ComputeInsights
import app.fuelwise.core.domain.insights.InsightPeriod
import app.fuelwise.core.domain.insights.InsightsReport
import app.fuelwise.core.domain.insights.VehicleHistory
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.Vehicle
import java.time.ZoneId
import javax.inject.Inject
import app.fuelwise.core.domain.units.DisplayUnits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What the Insights screens show for one scope (a vehicle or all vehicles) and period. */
data class InsightsSnapshot(
    val vehicles: List<Vehicle>,
    /** The switcher's vehicle; null with no vehicles. */
    val selected: Vehicle?,
    val allVehicles: Boolean,
    val period: InsightPeriod,
    val categories: Map<String, Category>,
    val report: InsightsReport?,
    /** Display units (PRD F-18): the vehicle's own, or the default for "All vehicles". */
    val units: DisplayUnits = DisplayUnits.INDIA,
)

/** Shared by the overview and chart detail, so both always agree on the numbers. */
class InsightsSource @Inject constructor(
    private val vehicles: VehicleRepository,
    private val entries: EntryRepository,
    private val categories: CategoryRepository,
    private val userData: UserDataRepository,
    private val compute: ComputeInsights,
    private val zoneId: ZoneId,
) {
    fun observe(allVehicles: Boolean, period: InsightPeriod): Flow<InsightsSnapshot> = combine(
        vehicles.observeVehicles(),
        userData.userData,
        entries.observeAllRefuels(),
        entries.observeAllExpenses(),
        categories.observeCategories(),
    ) { list, data, refuels, expenses, cats ->
        val selected = list.firstOrNull { it.id == data.selectedVehicleId } ?: list.firstOrNull()
        val scope = if (allVehicles) list else listOfNotNull(selected)
        val histories = scope.map { v ->
            VehicleHistory(v, refuels.filter { it.vehicleId == v.id }, expenses.filter { it.vehicleId == v.id })
        }
        InsightsSnapshot(
            vehicles = list,
            selected = selected,
            allVehicles = allVehicles,
            period = period,
            categories = cats.associateBy { it.id },
            report = if (histories.isEmpty()) {
                null
            } else {
                compute(
                    histories,
                    cats,
                    period,
                    zoneId,
                    vehicleCount = list.size,
                )
            },
            units = data.units.forVehicle(if (allVehicles) null else selected?.id),
        )
    }

    suspend fun select(vehicleId: String) = userData.setSelectedVehicle(vehicleId)
}
