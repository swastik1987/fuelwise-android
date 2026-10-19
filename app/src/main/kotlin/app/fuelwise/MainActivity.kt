package app.fuelwise

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.data.repository.ThemeMode
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.core.ui.locale.AppLanguage
import app.fuelwise.lock.AppLockScreen
import app.fuelwise.lock.AppLockSession
import app.fuelwise.navigation.FuelwiseApp
import app.fuelwise.navigation.OpenRequest
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject

// A FragmentActivity because BiometricPrompt (the app lock, PRD F-18) needs one.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var clock: Clock

    /** Set by a notification tap; consumed once the nav graph has opened the screen. */
    private var openRequest by mutableStateOf<OpenRequest?>(null)

    // Below Android 13 the per-app language is applied here (PRD F-18); 13+ does it in the platform.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readOpenRequest(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? MainActivityUiState.Ready
            val dark = when (ready?.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(ready?.appLock) {
                // Off, or turned on just now while the app is open: nothing to unlock until the next check.
                if (ready != null && !ready.appLock) AppLockSession.release()
                // Keep the Recents thumbnail blank while the lock is on.
                if (ready != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!ready.appLock)
                }
            }
            FuelwiseTheme(darkTheme = dark, dynamicColor = ready?.dynamicColor == true) {
                when (val s = state) {
                    // Preferences load in a few ms; the window background (bg) shows meanwhile.
                    MainActivityUiState.Loading -> Box(Modifier.fillMaxSize().background(FwTheme.colors.bg))

                    is MainActivityUiState.Ready -> Box(Modifier.fillMaxSize()) {
                        // Decided once per launch, so finishing onboarding doesn't rebuild the nav graph.
                        val startWithOnboarding = remember { !s.onboardingCompleted }
                        FuelwiseApp(
                            startWithOnboarding = startWithOnboarding,
                            openRequest = openRequest,
                            onOpenHandled = { openRequest = null },
                        )
                        if (s.appLock && AppLockSession.locked) AppLockScreen(onUnlocked = AppLockSession::release)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val ready = viewModel.uiState.value as? MainActivityUiState.Ready ?: return
        AppLockSession.onStart(ready.appLock, ready.appLockTimeoutMinutes, clock.millis())
    }

    override fun onStop() {
        super.onStop()
        // A rotation or a language change isn't leaving the app.
        if (!isChangingConfigurations) AppLockSession.onStop(clock.millis())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpenRequest(intent)
    }

    private fun readOpenRequest(intent: Intent?) {
        val target = intent?.getStringExtra(NotificationRoutes.EXTRA_OPEN) ?: return
        openRequest = OpenRequest(target, System.nanoTime(), intent.getStringExtra(NotificationRoutes.EXTRA_ARG))
        intent.removeExtra(NotificationRoutes.EXTRA_OPEN)
        intent.removeExtra(NotificationRoutes.EXTRA_ARG)
    }
}
