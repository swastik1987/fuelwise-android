package app.fuelwise.feature.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.component.FwChoiceChip
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.entry.EntryRangePreset
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.ui.entry.EntryDateRangePicker
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.entry.categoryName
import app.fuelwise.core.ui.format.IndianNumberFormat

/**
 * The Logbook's filter sheet (PRD F-05): date range, expense categories and payment methods, on top of the vehicle,
 * type and search filters already on screen. Changes apply on "Show results"; "Clear" resets the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LogbookFilterSheet(
    current: LogbookSheetFilters,
    categories: List<Category>,
    onApply: (LogbookSheetFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var pickingDates by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            Text(stringResource(R.string.logbook_filters_title), style = FwTheme.type.h3, color = FwTheme.colors.ink)

            FwOverline(stringResource(R.string.logbook_filters_dates))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
            ) {
                EntryRangePreset.entries.forEach { preset ->
                    FwChoiceChip(stringResource(rangeLabel(preset)), draft.range == preset, {
                        if (preset ==
                            EntryRangePreset.Custom
                        ) {
                            pickingDates = true
                        } else {
                            draft = draft.copy(range = preset)
                        }
                    })
                }
            }
            if (draft.range == EntryRangePreset.Custom && draft.customFrom != null && draft.customTo != null) {
                Text(
                    stringResource(
                        R.string.logbook_filters_custom_range,
                        IndianNumberFormat.date(draft.customFrom!!),
                        IndianNumberFormat.date(draft.customTo!!),
                    ),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                )
            }

            FwOverline(stringResource(R.string.logbook_filters_categories))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
            ) {
                categories.filterNot { it.isHidden }.forEach { c ->
                    FwFilterChip(categoryName(c), c.id in draft.categoryIds, {
                        draft = draft.copy(categoryIds = draft.categoryIds.toggle(c.id))
                    })
                }
            }

            FwOverline(stringResource(R.string.logbook_filters_payment))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
            ) {
                PaymentMethod.entries.forEach { p ->
                    FwFilterChip(stringResource(EntryLabels.payment(p)), p in draft.payments, {
                        draft = draft.copy(payments = draft.payments.toggle(p))
                    })
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = FwTheme.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
            ) {
                FwTextButton(stringResource(R.string.logbook_filters_clear), {
                    draft = LogbookSheetFilters()
                }, Modifier.weight(1f))
                FwPrimaryButton(stringResource(R.string.logbook_filters_apply), { onApply(draft) }, Modifier.weight(1f))
            }
        }
    }
    if (pickingDates) {
        EntryDateRangePicker(
            initialFrom = draft.customFrom,
            initialTo = draft.customTo,
            onPicked = { from, to ->
                draft = draft.copy(range = EntryRangePreset.Custom, customFrom = from, customTo = to)
                pickingDates = false
            },
            onDismiss = { pickingDates = false },
        )
    }
}

private fun <T> Set<T>.toggle(item: T) = if (item in this) this - item else this + item

private fun rangeLabel(preset: EntryRangePreset) = when (preset) {
    EntryRangePreset.All -> R.string.logbook_range_all
    EntryRangePreset.ThisMonth -> R.string.logbook_range_month
    EntryRangePreset.LastThreeMonths -> R.string.logbook_range_three_months
    EntryRangePreset.ThisYear -> R.string.logbook_range_year
    EntryRangePreset.Custom -> R.string.logbook_range_custom
}
