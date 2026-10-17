package app.fuelwise.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

@Composable
internal fun PrivacyScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: PrivacyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    PrivacyContent(state, onBack, viewModel::setWeeklyDigest, viewModel::setMonthlyRecap, viewModel::finish)
}

@Composable
internal fun PrivacyContent(
    state: PrivacyUiState,
    onBack: () -> Unit,
    onWeeklyDigest: (Boolean) -> Unit,
    onMonthlyRecap: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(title = "", onBack = onBack, backContentDescription = stringResource(R.string.onboarding_back))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = FwTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
                Text(stringResource(R.string.privacy_title), style = FwTheme.type.h1, color = FwTheme.colors.ink)
                Text(stringResource(R.string.privacy_sub), style = FwTheme.type.body, color = FwTheme.colors.ink2)
            }
            FwCard(contentPadding = PaddingValues(horizontal = FwTheme.spacing.l)) {
                FwSwitchRow(
                    title = stringResource(R.string.privacy_weekly),
                    subtitle = stringResource(R.string.privacy_weekly_sub),
                    checked = state.weeklyDigest,
                    onCheckedChange = onWeeklyDigest,
                )
                HorizontalDivider(color = FwTheme.colors.line)
                FwSwitchRow(
                    title = stringResource(R.string.privacy_monthly),
                    subtitle = stringResource(R.string.privacy_monthly_sub),
                    checked = state.monthlyRecap,
                    onCheckedChange = onMonthlyRecap,
                )
            }
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
                Icon(painterResource(FwIcons.Lock), null, tint = FwTheme.colors.ink2, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.privacy_note), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
            }
        }
        Box(Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s)) {
            FwPrimaryButton(stringResource(R.string.privacy_done), onDone, enabled = !state.saving)
        }
    }
}
