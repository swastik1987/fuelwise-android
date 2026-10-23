package app.fuelwise.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fuelwise.core.data.repository.UserData
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwChoiceSheet
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.insights.OwnershipCost
import app.fuelwise.core.ui.format.IndianNumberFormat

/**
 * Total cost of ownership (PRD F-03.5): per month and per km for the period, with what it's made of. The spread is
 * one setting for every vehicle, changed here.
 */
@Composable
internal fun OwnershipCard(tco: OwnershipCost, onYears: (Int) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val years = pluralStringResource(R.plurals.insights_tco_years, tco.years, tco.years)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FwOverline(stringResource(R.string.insights_tco))
        FwCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    IndianNumberFormat.rupeesWhole(tco.perMonth),
                    style = FwTheme.type.h2,
                    color = FwTheme.colors.ink,
                )
                Text(
                    stringResource(R.string.insights_tco_per_month),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                tco.perKm?.let {
                    Text(
                        stringResource(R.string.insights_tco_per_km, IndianNumberFormat.rupees(it)),
                        style = FwTheme.type.label,
                        color = FwTheme.colors.ink,
                    )
                }
            }
            Line(stringResource(R.string.insights_tco_running), IndianNumberFormat.rupeesWhole(tco.running))
            HorizontalDivider(color = FwTheme.colors.line)
            Line(
                stringResource(
                    R.string.insights_tco_purchase,
                    IndianNumberFormat.rupeesWhole(tco.purchasePrice),
                    years,
                ),
                IndianNumberFormat.rupeesWhole(tco.purchaseShare),
            )
            HorizontalDivider(color = FwTheme.colors.line)
            Line(stringResource(R.string.insights_tco_total), IndianNumberFormat.rupeesWhole(tco.total), bold = true)
            FwTextButton(stringResource(R.string.insights_tco_spread, years), { choosing = true })
        }
    }
    if (choosing) {
        val choices = UserData.OWNERSHIP_YEAR_CHOICES
        FwChoiceSheet(
            title = stringResource(R.string.insights_tco_spread_title),
            options = choices.map { pluralStringResource(R.plurals.insights_tco_years, it, it) },
            selectedIndex = choices.indexOf(tco.years),
            onSelect = {
                choosing = false
                onYears(choices[it])
            },
            onDismiss = { choosing = false },
            subtitle = stringResource(R.string.insights_tco_spread_hint),
        )
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) FwTheme.type.label else FwTheme.type.bodyS, color = FwTheme.colors.ink)
    }
}
