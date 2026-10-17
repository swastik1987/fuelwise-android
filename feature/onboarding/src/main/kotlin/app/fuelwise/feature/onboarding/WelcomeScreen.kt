package app.fuelwise.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.component.FwLogoMark
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

/**
 * Welcome (design.md screen 1): the promise, three trust lines, and a way back in for people restoring a backup
 * (PRD F-14.7).
 */
@Composable
internal fun WelcomeScreen(onGetStarted: () -> Unit, onRestore: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .background(FwTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.xl, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FwLogoMark()
                Text(stringResource(R.string.welcome_brand), style = FwTheme.type.h3, color = FwTheme.colors.ink)
            }
            Spacer(Modifier.weight(1f).size(FwTheme.spacing.huge))
            Text(
                stringResource(R.string.welcome_headline),
                style = FwTheme.type.display,
                color = FwTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.welcome_sub),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(top = FwTheme.spacing.m),
            )
            HorizontalDivider(
                color = FwTheme.colors.line,
                modifier = Modifier.padding(top = FwTheme.spacing.xxl, bottom = FwTheme.spacing.xl),
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TrustLine(FwIcons.NoAds, stringResource(R.string.welcome_trust_ads))
                TrustLine(FwIcons.NoAccount, stringResource(R.string.welcome_trust_account))
                TrustLine(FwIcons.CloudDone, stringResource(R.string.welcome_trust_drive))
            }
        }
        FwPrimaryButton(
            text = stringResource(R.string.welcome_get_started),
            onClick = onGetStarted,
            modifier = Modifier.padding(horizontal = FwTheme.spacing.l).padding(top = FwTheme.spacing.m),
        )
        FwTextButton(
            stringResource(R.string.welcome_restore),
            onRestore,
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = FwTheme.spacing.s),
        )
    }
}

@Composable
private fun TrustLine(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = FwTheme.colors.ink2,
            modifier = Modifier.size(22.dp),
        )
        Text(text, style = FwTheme.type.body, color = FwTheme.colors.ink)
    }
}
