package app.fuelwise.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.fuelwise.core.notifications.NotificationRoutes

/**
 * Opens the app at a screen, the way notification taps do: MainActivity reads [NotificationRoutes.EXTRA_OPEN] and the
 * nav graph opens the route (app's FuelwiseApp). A null [target] just opens the app.
 */
internal object WidgetLinks {
    fun open(context: Context, target: String? = null, arg: String? = null): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        target?.let { intent.putExtra(NotificationRoutes.EXTRA_OPEN, it) }
        arg?.let { intent.putExtra(NotificationRoutes.EXTRA_ARG, it) }
        // PendingIntents ignore extras when matching, so each target gets its own data: "Fuel" and "Charge" must not
        // collapse into one. The activity reads only the extras.
        intent.data = Uri.Builder().scheme(LINK_SCHEME).authority(target ?: HOME).apply {
            arg?.let(::appendPath)
        }.build()
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private const val LINK_SCHEME = "fuelwise-open"
    private const val HOME = "home"
}
