package app.fuelwise.core.location

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.fuelwise.core.domain.trip.PhoneBrand
import app.fuelwise.core.domain.trip.phoneBrand

/**
 * Phone-maker battery savers (PRD F-21.7). Opens the screen where this phone lets the app run in the background, and
 * checks what Android itself reports. No REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (CLAUDE.md rule 6): the user changes
 * the setting on the system screen.
 */
object BatterySaverHelp {
    val brand: PhoneBrand get() = phoneBrand(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())

    /**
     * Tries the brand's own screen first (they move between OS versions, so each is a guess), then Android's
     * battery-optimisation list, then the app's details page. Returns false only if nothing opened.
     */
    fun openSettings(context: Context, brand: PhoneBrand = this.brand): Boolean {
        val candidates = brandScreens(brand) + listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
        return candidates.any { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                // Some makers export the screen but guard it; fall through to the next.
                false
            }
        }
    }

    /**
     * What Android reports: battery use "Unrestricted" (not optimised) and no background restriction. A maker's own
     * autostart switch can't be read, so a pass here is necessary but not always sufficient.
     */
    fun isUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val ignoring = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        return ignoring && !restricted
    }

    // Known screens, per https://dontkillmyapp.com; tried in order.
    private fun brandScreens(brand: PhoneBrand): List<Intent> = when (brand) {
        PhoneBrand.Xiaomi -> listOf(
            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )

        PhoneBrand.Samsung -> listOf(
            component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )

        PhoneBrand.Oppo -> listOf(
            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )

        PhoneBrand.Vivo -> listOf(
            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )

        PhoneBrand.Huawei -> listOf(
            component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )

        PhoneBrand.Other -> emptyList()
    }

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
}
