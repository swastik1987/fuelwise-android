package app.fuelwise.core.ui.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.UUID

/**
 * The app lock's check (PRD F-18): fingerprint or face, with the phone's PIN, pattern or password as the fallback.
 * BIOMETRIC_WEAK with DEVICE_CREDENTIAL is the combination BiometricPrompt supports from Android 9 on (STRONG with
 * DEVICE_CREDENTIAL isn't supported on Android 9–10).
 *
 * Android 8.x gets the platform's confirm-credential screen instead: BiometricPrompt's compat fingerprint dialog there
 * is an AppCompat dialog, which our platform window theme can't host.
 */
object DeviceAuth {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the phone has no screen lock at all: the lock can't be turned on then. */
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true ||
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt. [onResult] gets true once the user is verified, false when they cancel or it fails
     * for good (too many attempts). A single wrong fingerprint isn't a result: the prompt stays up.
     */
    fun prompt(activity: FragmentActivity, title: String, subtitle: String?, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            confirmCredential(activity, title, subtitle, onResult)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        // No negative button: with DEVICE_CREDENTIAL allowed the system shows "Use PIN" instead.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    // Deprecated from Android 10 in favour of BiometricPrompt, which is what 9+ uses above.
    @Suppress("DEPRECATION")
    private fun confirmCredential(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onResult: (Boolean) -> Unit,
    ) {
        val intent = activity.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(title, subtitle)
        if (intent == null) {
            // No screen lock: nothing to check against, and the lock can't be on (see isAvailable).
            onResult(false)
            return
        }
        lateinit var launcher: ActivityResultLauncher<Intent>
        launcher = activity.activityResultRegistry.register(
            "fw_device_auth_${UUID.randomUUID()}",
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            launcher.unregister()
            onResult(result.resultCode == Activity.RESULT_OK)
        }
        launcher.launch(intent)
    }
}

/** The hosting activity, for BiometricPrompt. Compose's LocalContext may be a wrapper around it. */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
