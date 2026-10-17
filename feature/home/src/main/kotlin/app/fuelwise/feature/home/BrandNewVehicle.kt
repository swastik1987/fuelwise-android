package app.fuelwise.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme

@Composable
internal fun BrandNewVehicle(fullFills: Int, onLogFuel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl)) {
        FwCard(contentPadding = PaddingValues(FwTheme.spacing.xl)) {
            FwOverline(stringResource(R.string.home_unlock_overline))
            Text(
                stringResource(R.string.home_unlock_title),
                style = FwTheme.type.h1,
                color = FwTheme.colors.ink,
                modifier = Modifier.padding(top = FwTheme.spacing.l),
            )
            BaselineProgress(
                completedSteps = fullFills.coerceAtMost(2),
                modifier = Modifier.padding(top = FwTheme.spacing.l),
            )
            FwInkButton(
                text = stringResource(R.string.home_log_fuel),
                onClick = onLogFuel,
                leadingIcon = FwIcons.Fuel,
                modifier = Modifier.padding(top = FwTheme.spacing.l),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Icon(
                painterResource(FwIcons.Info),
                contentDescription = null,
                tint = FwTheme.colors.ink2,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.home_unlock_explainer),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun BaselineProgress(completedSteps: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { step ->
                val color = if (step < completedSteps) FwTheme.colors.ink else FwTheme.colors.surface2
                Box(Modifier.weight(1f).height(6.dp).background(color, FwTheme.radius.pill))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.home_step_baseline),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.home_step_first_mileage),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
            )
        }
    }
}
