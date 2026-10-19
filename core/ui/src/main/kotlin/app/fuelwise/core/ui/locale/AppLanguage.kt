package app.fuelwise.core.ui.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Per-app language (PRD F-18: English and Hindi at launch). On Android 13+ this is the platform's per-app locale
 * (LocaleManager): the system stores it, shows it in system settings and restarts the activity itself. Older
 * versions have no platform API, so the choice is kept in a small private preferences file that the activity reads
 * synchronously in attachBaseContext, before any DataStore is available, and the caller recreates the activity.
 */
object AppLanguage {
    /** Language tags offered in Settings. Empty = follow the phone's language. */
    const val SYSTEM = ""
    val CHOICES: List<String> = listOf(SYSTEM, "en", "hi")

    /** Whether the caller must recreate the activity after [set]; Android 13+ does it itself. */
    val needsRecreate: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun current(context: Context): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        platformCurrent(context)
    } else {
        legacyPrefs(context).getString(KEY, SYSTEM).orEmpty()
    }

    fun set(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            platformSet(context, tag)
        } else {
            legacyPrefs(context).edit().putString(KEY, tag).apply()
        }
    }

    /** For attachBaseContext below Android 13: applies the stored choice, or returns [base] unchanged. */
    fun wrap(base: Context): Context {
        if (!needsRecreate) return base
        val tag = legacyPrefs(base).getString(KEY, SYSTEM).orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun platformCurrent(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) SYSTEM else locales[0].language
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun platformSet(context: Context, tag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    private fun legacyPrefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "fw_app_language"
    private const val KEY = "tag"
}
