package app.fuelwise.core.data.repository

import android.content.Context
import app.fuelwise.core.domain.Dispatcher
import app.fuelwise.core.domain.FwDispatcher
import app.fuelwise.core.domain.reminder.PresetRule
import app.fuelwise.core.domain.reminder.ReminderRules
import app.fuelwise.core.model.EmissionNorm
import app.fuelwise.core.model.ReminderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Reminder defaults from the versioned rules file (CLAUDE.md rule 8). */
interface RulesRepository {
    suspend fun reminderRules(): ReminderRules
}

@Singleton
internal class AssetRulesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(FwDispatcher.IO) private val io: CoroutineDispatcher,
) : RulesRepository {
    private val mutex = Mutex()
    private var cached: ReminderRules? = null

    override suspend fun reminderRules(): ReminderRules = mutex.withLock {
        cached ?: withContext(io) {
            parseRules(context.assets.open(RULES_FILE).bufferedReader().use { it.readText() })
        }.also { cached = it }
    }

    private companion object {
        const val RULES_FILE = "rules/india.json"
    }
}

/** Unknown keys are ignored and missing ones fall back, so an older app reads a newer file. */
internal fun parseRules(json: String): ReminderRules {
    val root = JSONObject(json)
    val puc = root.optJSONObject("puc") ?: JSONObject()
    val window = root.optJSONObject("odometerWindowKm") ?: JSONObject()
    val presets = root.optJSONObject("presets") ?: JSONObject()
    val byNorm = puc.optJSONObject("monthsByNorm") ?: JSONObject()
    return ReminderRules(
        version = root.optInt("version", 0),
        presets = ReminderKind.entries.mapNotNull { kind -> presets.optJSONObject(kind.name)?.let { kind to it.toRule() } }.toMap(),
        pucMonthsByNorm = EmissionNorm.entries.mapNotNull { norm ->
            if (byNorm.has(norm.name)) norm to byNorm.getInt(norm.name) else null
        }.toMap(),
        pucDefault = PresetRule(
            months = puc.optIntOrNull("defaultMonths"),
            notifyDaysBefore = puc.optJSONArray("notifyDaysBefore").toInts(),
        ),
        odometerWindowKmCar = window.optDouble("car", DEFAULT_WINDOW_CAR),
        odometerWindowKmTwoWheeler = window.optDouble("twoWheeler", DEFAULT_WINDOW_2W),
    )
}

private fun JSONObject.toRule(): PresetRule = PresetRule(
    months = optIntOrNull("months"),
    km = if (has("km")) getDouble("km") else null,
    notifyDaysBefore = optJSONArray("notifyDaysBefore").toInts(),
    twoWheeler = optJSONObject("twoWheeler")?.toRule(),
)

private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) getInt(key) else null

private fun JSONArray?.toInts(): List<Int> = if (this == null) emptyList() else (0 until length()).map { getInt(it) }

private const val DEFAULT_WINDOW_CAR = 300.0
private const val DEFAULT_WINDOW_2W = 150.0
