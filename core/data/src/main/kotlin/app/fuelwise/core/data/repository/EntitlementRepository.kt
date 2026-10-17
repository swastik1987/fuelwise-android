package app.fuelwise.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The only place Pro is checked (CLAUDE.md). Play Billing backs it in M11 (F-19). */
interface EntitlementRepository {
    val isPro: Flow<Boolean>
}

/** Until billing exists, everyone is on the free tier. */
internal class FreeTierEntitlementRepository @Inject constructor() : EntitlementRepository {
    override val isPro: Flow<Boolean> = flowOf(false)
}
