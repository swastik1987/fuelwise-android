package app.fuelwise.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.City

@Composable
internal fun HistoryScreen(onBack: () -> Unit, onContinue: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(state, onBack, onContinue, viewModel::onQueryChange, viewModel::onCitySelected)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCitySelected: (City) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg).imePadding()) {
        FwTopBar(
            title = "",
            onBack = onBack,
            backContentDescription = stringResource(R.string.onboarding_back),
            actions = { FwTextButton(stringResource(R.string.history_skip), onContinue) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = FwTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
                Text(stringResource(R.string.history_title), style = FwTheme.type.h1, color = FwTheme.colors.ink)
                Text(stringResource(R.string.history_sub), style = FwTheme.type.body, color = FwTheme.colors.ink2)
            }
            Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs)) {
                // Import presets arrive with F-13 (M10). Shown so people know they're coming; not tappable yet.
                OptionRow(
                    FwIcons.UploadFile,
                    stringResource(R.string.history_import_fuelio),
                    stringResource(R.string.history_coming),
                    selected = false,
                    enabled = false,
                )
                OptionRow(
                    FwIcons.Spreadsheet,
                    stringResource(R.string.history_import_other),
                    stringResource(R.string.history_coming),
                    selected = false,
                    enabled = false,
                )
                OptionRow(
                    FwIcons.StartFresh,
                    stringResource(R.string.history_fresh),
                    stringResource(R.string.history_fresh_sub),
                    selected = true,
                    enabled = true,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
                FwOverline(stringResource(R.string.history_city))
                FwTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = stringResource(R.string.history_city_search),
                    placeholder = state.selected?.name,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                )
                if (state.results.isNotEmpty()) {
                    Column {
                        state.results.forEach { city -> CityResult(city, onClick = { onCitySelected(city) }) }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val chips = (state.popular + listOfNotNull(state.selected)).distinct()
                    chips.forEach { city ->
                        FwFilterChip(city.name, selected = city == state.selected, onClick = { onCitySelected(city) })
                    }
                }
                Text(
                    stringResource(R.string.history_city_privacy),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                )
            }
        }
        Box(Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s)) {
            FwInkButton(stringResource(R.string.onboarding_continue), onContinue)
        }
    }
}

@Composable
private fun OptionRow(icon: Int, title: String, subtitle: String, selected: Boolean, enabled: Boolean) {
    val colors = FwTheme.colors
    Surface(
        shape = FwTheme.radius.card,
        color = colors.surface,
        border = when {
            selected -> BorderStroke(1.5.dp, colors.ink)
            colors.cardBorder != Color.Transparent -> BorderStroke(1.dp, colors.cardBorder)
            else -> null
        },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(colors.surface2, FwTheme.radius.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, tint = if (enabled) colors.ink else colors.ink3)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = if (enabled) colors.ink else colors.ink2,
                )
                Text(subtitle, style = FwTheme.type.bodyS, color = colors.ink2)
            }
            if (selected) Icon(painterResource(FwIcons.CheckCircleFilled), null, tint = colors.ink)
        }
    }
}

@Composable
private fun CityResult(city: City, onClick: () -> Unit) {
    Surface(onClick = onClick, color = FwTheme.colors.bg) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(FwIcons.Search), null, tint = FwTheme.colors.ink3, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = FwTheme.spacing.s)) {
                Text(city.name, style = FwTheme.type.body, color = FwTheme.colors.ink)
                Text(city.state, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
            }
        }
    }
}
