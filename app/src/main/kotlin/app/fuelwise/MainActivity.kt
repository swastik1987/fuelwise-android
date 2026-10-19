package app.fuelwise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.data.repository.ThemeMode
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.notifications.NotificationRoutes
import app.fuelwise.navigation.FuelwiseApp
import app.fuelwise.navigation.OpenRequest
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    /** Set by a notification tap; consumed once the nav graph has opened the screen. */
    private var openRequest by mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readOpenRequest(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = when ((state as? MainActivityUiState.Ready)?.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                else -> isSystemInDarkTheme()
            }
            FuelwiseTheme(darkTheme = dark) {
                when (val s = state) {
                    // Preferences load in a few ms; the window background (bg) shows meanwhile.
                    MainActivityUiState.Loading -> Box(Modifier.fillMaxSize().background(FwTheme.colors.bg))

                    is MainActivityUiState.Ready -> {
                        // Decided once per launch, so finishing onboarding doesn't rebuild the nav graph.
                        val startWithOnboarding = remember { !s.onboardingCompleted }
                        FuelwiseApp(
                            startWithOnboarding = startWithOnboarding,
                            openRequest = openRequest,
                            onOpenHandled = { openRequest = null },
                        )
                    }
                }
            }
        }
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
