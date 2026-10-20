package app.fuelwise.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fuelwise.R
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwLogoMark
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.privacy.AppLockPolicy
import app.fuelwise.core.ui.privacy.DeviceAuth
import app.fuelwise.core.ui.privacy.findFragmentActivity

/**
 * App lock state for this process (PRD F-18). It lives outside the activity so a rotation or a language change
 * doesn't lock again, and dies with the process, so a cold start always locks.
 */
internal object AppLockSession {
    /** Starts locked: on a cold start the lock applies until the preferences say it's off. */
    var locked by mutableStateOf(true)

    /** True while our own prompt is up: its credential screen stops the activity, which mustn't count as leaving. */
    var authenticating = false

    private var backgroundedAtUtcMs: Long? = null

    fun onStop(nowUtcMs: Long) {
        if (!authenticating) backgroundedAtUtcMs = nowUtcMs
    }

    fun onStart(enabled: Boolean, timeoutMinutes: Int, nowUtcMs: Long) {
        val since = backgroundedAtUtcMs ?: return
        backgroundedAtUtcMs = null
        if (AppLockPolicy.shouldLock(enabled, timeoutMinutes, since, nowUtcMs)) locked = true
    }

    /** The lock is off, or was just turned on while the app is open: nothing to unlock. */
    fun release() {
        locked = false
    }
}

/**
 * Full-screen cover while locked. It asks right away and offers "Unlock" to ask again after a cancel. The app stays
 * composed underneath so navigation state survives; the cover is opaque and swallows every touch.
 */
@Composable
internal fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var asking by remember { mutableStateOf(false) }
    val ask = {
        val activity = context.findFragmentActivity()
        if (activity != null && !asking) {
            asking = true
            AppLockSession.authenticating = true
            DeviceAuth.prompt(activity, resources.getString(R.string.lock_prompt_title), null) { ok ->
                asking = false
                AppLockSession.authenticating = false
                if (ok) onUnlocked()
            }
        }
    }
    LaunchedEffect(Unit) { ask() }
    // Back mustn't reach the screens underneath: it leaves the app instead.
    BackHandler { context.findFragmentActivity()?.moveTaskToBack(true) }
    Column(
        Modifier
            .fillMaxSize()
            .background(FwTheme.colors.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m, Alignment.CenterVertically),
    ) {
        FwLogoMark(size = 56.dp)
        Text(
            stringResource(R.string.lock_title),
            style = FwTheme.type.h2,
            color = FwTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.lock_body),
            style = FwTheme.type.body,
            color = FwTheme.colors.ink2,
            textAlign = TextAlign.Center,
        )
        FwInkButton(
            stringResource(R.string.lock_unlock),
            onClick = ask,
            modifier = Modifier.fillMaxWidth().padding(top = FwTheme.spacing.l),
        )
    }
}
