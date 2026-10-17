package app.fuelwise.feature.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme

/** Empty state until data exists. Content arrives with its milestone in docs/development-plan.md. */
@Composable
internal fun GarageScreen(onPrimaryAction: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(FwTheme.colors.bg), contentAlignment = Alignment.Center) {
        FwEmptyState(
            icon = FwIcons.Garage,
            headline = stringResource(R.string.garage_empty_title),
            body = stringResource(R.string.garage_empty_body),
            actionLabel = stringResource(R.string.garage_empty_action),
            onAction = onPrimaryAction,
        )
    }
}

@Preview
@Composable
private fun GarageScreenPreview() {
    FuelwiseTheme { GarageScreen(onPrimaryAction = {}) }
}
