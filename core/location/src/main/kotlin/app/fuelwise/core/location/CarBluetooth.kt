package app.fuelwise.core.location

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import app.fuelwise.core.data.repository.CarLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Car Bluetooth for automatic trips (PRD F-21.3 start signal 2), through Companion Device Manager: the user picks
 * the car in the system's own chooser, and Android then tells the app when the car connects and disconnects, even
 * with the app closed. Android 12+ only (device presence); nothing to scan, and no Bluetooth permission needed.
 */
interface CarPresence {
    /** Android 12+ with companion device support. */
    val supported: Boolean

    fun observe(link: CarLink)

    /** Unlinking: stop observing and drop the association. */
    fun forget(link: CarLink)
}

internal class CompanionCarPresence @Inject constructor(@param:ApplicationContext private val context: Context) :
    CarPresence {
    private val manager: CompanionDeviceManager? by lazy {
        if (supported) context.getSystemService(CompanionDeviceManager::class.java) else null
    }

    override val supported: Boolean
        get() = carBluetoothSupported(context)

    // REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE is a normal permission declared in the manifest.
    @SuppressLint("MissingPermission")
    override fun observe(link: CarLink) {
        val cdm = manager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && link.associationId > 0) {
                cdm.startObservingDevicePresence(presenceRequest(link.associationId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startObservingByAddress(cdm, link.address)
            }
        } catch (_: RuntimeException) {
            // The association was removed in system settings: nothing to observe until the user links again.
        }
    }

    @SuppressLint("MissingPermission")
    override fun forget(link: CarLink) {
        val cdm = manager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && link.associationId > 0) {
                cdm.stopObservingDevicePresence(presenceRequest(link.associationId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stopObservingByAddress(cdm, link.address)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && link.associationId > 0) {
                cdm.disassociate(link.associationId)
            } else {
                disassociateByAddress(cdm, link.address)
            }
        } catch (_: RuntimeException) {
            // Already gone.
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun presenceRequest(associationId: Int) =
        ObservingDevicePresenceRequest.Builder().setAssociationId(associationId).build()

    // Android 12–15 API; replaced by the request form above on 16.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun startObservingByAddress(cdm: CompanionDeviceManager, address: String) =
        cdm.startObservingDevicePresence(address)

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun stopObservingByAddress(cdm: CompanionDeviceManager, address: String) =
        cdm.stopObservingDevicePresence(address)

    @Suppress("DEPRECATION")
    private fun disassociateByAddress(cdm: CompanionDeviceManager, address: String) = cdm.disassociate(address)
}

/** Android 12+ (presence events) on a device with companion device support. */
fun carBluetoothSupported(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

/**
 * Starts the system "pick your car" chooser and hands back the link, or null if unsupported here. The chooser lists
 * Bluetooth devices; the user picks one. The app never scans or reads other devices.
 */
@Composable
fun rememberCarBluetoothLinker(onLinked: (CarLink) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        result.data?.let(::linkFromResult)?.let(onLinked)
    }
    return remember(context, launcher) {
        if (!carBluetoothSupported(context)) {
            null
        } else {
            {
                requestAssociation(context) { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
            }
        }
    }
}

private fun requestAssociation(context: Context, onChooser: (IntentSender) -> Unit) {
    val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
    val request = AssociationRequest.Builder()
        .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
        .setSingleDevice(false)
        .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        cdm.associate(
            request,
            context.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = onChooser(intentSender)

                override fun onFailure(error: CharSequence?) = Unit
            },
        )
    } else {
        associateLegacy(cdm, request, onChooser)
    }
}

// Android 12: the callback form before the executor overload existed.
@Suppress("DEPRECATION")
private fun associateLegacy(
    cdm: CompanionDeviceManager,
    request: AssociationRequest,
    onChooser: (IntentSender) -> Unit,
) {
    cdm.associate(
        request,
        object : CompanionDeviceManager.Callback() {
            @Deprecated("Android 12 path")
            override fun onDeviceFound(intentSender: IntentSender) = onChooser(intentSender)

            override fun onFailure(error: CharSequence?) = Unit
        },
        null,
    )
}

/** Android 13+ returns the association; Android 12 only the device, so its id stays 0 and presence is by address. */
private fun linkFromResult(data: Intent): CarLink? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val info =
            IntentCompat.getParcelableExtra(data, CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?: return null
        val address = info.deviceMacAddress?.toString() ?: return null
        return CarLink(address.uppercase(), info.id, info.displayName?.toString())
    }
    @Suppress("DEPRECATION")
    val device = IntentCompat.getParcelableExtra(data, CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
        ?: return null
    // The name would need BLUETOOTH_CONNECT on Android 12; the address doesn't.
    return CarLink(device.address.uppercase(), 0, null)
}
