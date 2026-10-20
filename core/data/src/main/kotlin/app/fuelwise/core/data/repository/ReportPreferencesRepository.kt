package app.fuelwise.core.data.repository

import app.fuelwise.core.datastore.PreferencesDataSource
import app.fuelwise.core.model.Money
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Choices the reports remember (PRD F-13.3): the business mileage rate (F-07.9). */
interface ReportPreferencesRepository {
    /** ₹ per km for business trips; null until the user sets one. */
    val businessRatePerKm: Flow<Money?>

    /** Null or zero clears it. */
    suspend fun setBusinessRatePerKm(rate: Money?)
}

internal class DataStoreReportPreferencesRepository @Inject constructor(private val source: PreferencesDataSource) :
    ReportPreferencesRepository {
    override val businessRatePerKm: Flow<Money?> =
        source.preferences.map { p -> p.businessRatePaisePerKm.takeIf { it > 0 }?.let(::Money) }

    override suspend fun setBusinessRatePerKm(rate: Money?) = source.update {
        businessRatePaisePerKm = rate?.paise?.coerceAtLeast(0) ?: 0
    }
}

// Bound here rather than in DataModule so the export work stays in its own files.
@Module
@InstallIn(SingletonComponent::class)
internal interface ReportPreferencesModule {
    @Binds
    fun reportPreferences(impl: DataStoreReportPreferencesRepository): ReportPreferencesRepository
}
