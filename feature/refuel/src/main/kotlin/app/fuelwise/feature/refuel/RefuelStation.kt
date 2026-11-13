package app.fuelwise.feature.refuel

import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.StationRepository
import app.fuelwise.core.domain.station.Station
import app.fuelwise.core.domain.station.StationMatch
import app.fuelwise.core.domain.station.StationOption
import app.fuelwise.core.domain.trip.FillNeed
import app.fuelwise.core.location.Fix
import app.fuelwise.core.location.SingleFix
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.Refuel
import app.fuelwise.core.model.Tank
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Station auto-detect for one Add fuel sheet (PRD F-21.1). One single location fix while the sheet is open (balanced
 * priority, 10 s timeout, a fix up to 2 minutes old is fine: CLAUDE.md rule 11), matched on the phone against the
 * bundled station list and the user's own past fill-ups. The position never leaves the phone (rule 5).
 */
class RefuelStation @Inject constructor(
    private val fix: SingleFix,
    private val stations: StationRepository,
    private val entries: EntryRepository,
) {
    private var listed: List<Pair<Station, Double>> = emptyList()
    private var history: List<Refuel> = emptyList()

    /** The phone's position from [locate]; null before, or when it was dropped (a back-dated entry). */
    var position: Fix? = null
        private set

    val hasPermission: Boolean get() = fix.hasPermission

    /** Takes one fix and loads what's near it. False without permission or a fix in time. */
    suspend fun locate(): Boolean {
        val here = fix.once() ?: return false
        listed = stations.near(here.lat, here.lng, StationMatch.WIDE_M)
        history = entries.observeAllRefuels().first()
        position = here
        return true
    }

    fun options(tank: Tank?): List<StationOption> {
        val here = position ?: return emptyList()
        return StationMatch.options(here.lat, here.lng, listed, history, tank.fillNeed())
    }

    fun forget() {
        position = null
    }

    private fun Tank?.fillNeed(): FillNeed = when (this?.energyType) {
        EnergyType.ELECTRIC -> FillNeed.Electric
        EnergyType.CNG -> FillNeed.Cng
        else -> FillNeed.Liquid
    }
}
