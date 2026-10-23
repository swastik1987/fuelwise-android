package app.fuelwise.feature.trips

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

// Permission checks for automatic trips setup (PRD F-21.3, §9). Asked only after the disclosure.

// Physical activity is a runtime permission from Android 10; before that it's granted at install.
internal fun needsActivityStep() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

internal fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasActivityRecognition() =
    !needsActivityStep() || granted(Manifest.permission.ACTIVITY_RECOGNITION)

internal fun Context.hasBackgroundLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(BACKGROUND_LOCATION))

// The "Start trip?" prompt and the drive notification need notifications; asked alongside, never required.
internal fun activityPermissions(): Array<String> = buildList {
    if (needsActivityStep()) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

// Android 10 still offers "Allow all the time" in the same dialog, so ask for it together there.
internal fun foregroundLocationPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) add(BACKGROUND_LOCATION)
}.toTypedArray()

internal fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

// A plain string, so inlining it is harmless; it is only asked for on Android 10 and later.
@SuppressLint("InlinedApi")
internal const val BACKGROUND_LOCATION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
