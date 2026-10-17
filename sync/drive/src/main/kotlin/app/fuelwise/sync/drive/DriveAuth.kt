package app.fuelwise.sync.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Qualifier for the Google web OAuth client ID, provided by the app from local.properties (setup step L1). */
const val WEB_CLIENT_ID_NAME = "googleWebClientId"

/** The result of asking for the drive.appdata scope. */
sealed interface DriveAccess {
    data class Granted(val accessToken: String) : DriveAccess

    /** The user confirms in a Google screen: the UI launches [intent] and passes the result to [DriveAuth.complete]. */
    data class NeedsConsent(val intent: PendingIntent) : DriveAccess

    /** No web client ID in local.properties yet (setup step L1). */
    data object NotConfigured : DriveAccess
}

/** Abstraction over sign-in and the Drive scope, so backup logic can be tested without Google Play services. */
interface DriveAuth {
    val isConfigured: Boolean

    /** Credential Manager "Sign in with Google" (PRD F-14.2). Needs an Activity context. Returns the account email. */
    suspend fun signIn(activityContext: Context): String

    /** Asks for drive.appdata for [email]; silent when already granted. */
    suspend fun authorize(email: String): DriveAccess

    /** Finishes a consent screen started from [DriveAccess.NeedsConsent]. */
    fun complete(data: Intent?): DriveAccess
}

@Singleton
internal class GoogleDriveAuth @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named(WEB_CLIENT_ID_NAME) private val webClientId: String,
) : DriveAuth {
    override val isConfigured: Boolean get() = webClientId.endsWith(".apps.googleusercontent.com")

    override suspend fun signIn(activityContext: Context): String {
        check(isConfigured) { "Google sign-in is not configured" }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request)
        } catch (e: NoCredentialException) {
            // No Google account on the phone: the screen says sign-in failed and offers the local backup file.
            throw IllegalStateException("No Google account on this device", e)
        }
        val credential = result.credential
        val isGoogle = credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        check(isGoogle) {
            "Unexpected credential"
        }
        return checkNotNull(GoogleIdTokenCredential.createFrom(credential.data).email) {
            "No email on the Google account"
        }
    }

    override suspend fun authorize(email: String): DriveAccess {
        if (!isConfigured) return DriveAccess.NotConfigured
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
            .setAccount(Account(email, ACCOUNT_TYPE))
            .build()
        return Identity.getAuthorizationClient(context).authorize(request).await().toAccess()
    }

    override fun complete(data: Intent?): DriveAccess =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).toAccess()

    private fun AuthorizationResult.toAccess(): DriveAccess {
        val pending = pendingIntent
        val token = accessToken
        return when {
            hasResolution() && pending != null -> DriveAccess.NeedsConsent(pending)
            token != null -> DriveAccess.Granted(token)
            else -> error("No access token")
        }
    }

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val ACCOUNT_TYPE = "com.google"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
