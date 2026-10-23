package app.fuelwise.core.location

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android binds this while a linked car's Bluetooth is in range (PRD F-21.3 start signal 2). A connection starts an
 * automatic trip for that car's vehicle; a disconnection ends it. Companion apps may start a foreground service from
 * here. Each Android version calls a different callback; [AutoTrips] treats repeats as no-ops.
 */
@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class CarPresenceService : CompanionDeviceService() {
    @Inject lateinit var autoTrips: AutoTrips

    // The recorder's scope: Android may unbind this service as soon as the car disconnects.
    @Inject
    @RecorderScope
    lateinit var scope: CoroutineScope

    // Android 12.
    @Deprecated("Android 12 callback")
    override fun onDeviceAppeared(address: String) = handle(address, 0, connected = true)

    @Deprecated("Android 12 callback")
    override fun onDeviceDisappeared(address: String) = handle(address, 0, connected = false)

    // Android 13–15.
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Deprecated("Android 13–15 callback")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) =
        handle(associationInfo.deviceMacAddress?.toString(), associationInfo.id, connected = true)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Deprecated("Android 13–15 callback")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) =
        handle(associationInfo.deviceMacAddress?.toString(), associationInfo.id, connected = false)

    // Android 16+.
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED, DevicePresenceEvent.EVENT_BLE_APPEARED ->
                handle(null, event.associationId, connected = true)

            DevicePresenceEvent.EVENT_BT_DISCONNECTED, DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                handle(null, event.associationId, connected = false)

            else -> Unit
        }
    }

    private fun handle(address: String?, associationId: Int, connected: Boolean) {
        scope.launch {
            if (connected) {
                autoTrips.onCarConnected(address, associationId)
            } else {
                autoTrips.onCarDisconnected(address, associationId)
            }
        }
    }
}
