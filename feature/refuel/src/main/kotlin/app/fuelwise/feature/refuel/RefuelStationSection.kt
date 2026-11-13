package app.fuelwise.feature.refuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import kotlin.math.roundToInt

/**
 * Station auto-detect (PRD F-21.1, design option "chip row"): a line saying where the suggestion came from, the best
 * match pre-selected with up to two alternatives and "Other" always shown, since pumps often face each other across
 * a road. "Other", or no station nearby, shows the name field here instead of under More.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StationSection(state: RefuelFormState, actions: RefuelActions) {
    val detect = state.stationDetect
    if (detect == StationDetect.Off) return
    Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
        FwOverline(stringResource(R.string.refuel_station))
        when (detect) {
            StationDetect.NeedsPermission -> {
                FwSecondaryButton(
                    stringResource(R.string.refuel_station_detect),
                    actions.onDetectStation,
                    leadingIcon = FwIcons.Map,
                )
                Text(
                    stringResource(R.string.refuel_station_detect_hint),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
            }

            StationDetect.Locating -> LocationLine(stringResource(R.string.refuel_station_locating))

            StationDetect.NotFound -> {
                LocationLine(stringResource(R.string.refuel_station_none))
                StationField(state, actions)
            }

            is StationDetect.Found -> {
                val chosen = detect.selectedOption
                LocationLine(
                    when {
                        chosen == null -> stringResource(R.string.refuel_station_other_hint)

                        chosen.usedBefore -> stringResource(
                            R.string.refuel_station_used_before,
                            chosen.distanceM.metres(),
                        )

                        else -> stringResource(R.string.refuel_station_detected, chosen.distanceM.metres())
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
                ) {
                    detect.options.forEachIndexed { index, option ->
                        FwChoiceChip(option.label, detect.selected == index, { actions.onStationOption(index) })
                    }
                    FwChoiceChip(
                        stringResource(R.string.refuel_station_other),
                        detect.selected == null,
                        { actions.onStationOption(null) },
                    )
                }
                if (detect.selected == null) StationField(state, actions)
            }

            StationDetect.Off -> Unit
        }
    }
}

@Composable
private fun LocationLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xxs),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            painterResource(FwIcons.Map),
            contentDescription = null,
            tint = FwTheme.colors.ink2,
            modifier = Modifier.size(FwTheme.size.iconSmall),
        )
        Text(text, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
    }
}

/** The station name field. Under More when nothing was detected; here when the user picks "Other". */
@Composable
internal fun StationField(state: RefuelFormState, actions: RefuelActions) {
    FwTextField(
        value = state.station,
        onValueChange = actions.onStation,
        label = stringResource(R.string.refuel_station),
        placeholder = stringResource(R.string.refuel_station_placeholder),
        modifier = Modifier.onFocusChanged { if (it.isFocused) actions.onFocus(null) },
    )
}

private fun Double.metres(): Int = roundToInt()
