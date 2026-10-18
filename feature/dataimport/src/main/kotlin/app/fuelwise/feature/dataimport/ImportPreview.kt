package app.fuelwise.feature.dataimport

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwFilterChip
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.importing.DateOrder
import app.fuelwise.core.domain.importing.EntryKind
import app.fuelwise.core.domain.importing.ImportField
import app.fuelwise.core.domain.importing.ImportPlanner
import app.fuelwise.core.domain.importing.RowIssue
import app.fuelwise.core.domain.importing.RowProblem
import app.fuelwise.core.model.ImportBatch
import app.fuelwise.core.model.ImportSource
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreviewStep(preview: PreviewState, state: ImportUiState, actions: ImportActions) {
    val plan = preview.plan
    ScrollingBody(
        bottom = {
            FwInkButton(
                if (plan.entryCount > 0) {
                    pluralStringResource(R.plurals.import_preview_confirm, plan.entryCount, plan.entryCount)
                } else {
                    stringResource(R.string.import_preview_nothing)
                },
                actions.onConfirmImport,
                Modifier.fillMaxWidth(),
                enabled = plan.entryCount > 0,
            )
        },
    ) {
        Text(
            listOfNotNull(stringResource(sourceLabel(preview.source)), preview.fileName).joinToString(" · "),
            style = FwTheme.type.body,
            color = FwTheme.colors.ink2,
        )
        if (preview.askVehicle && state.vehicles.size > 1) {
            FwOverline(stringResource(R.string.import_preview_vehicle))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.vehicles.forEach { v ->
                    FwFilterChip(v.name, selected = v.id == preview.vehicleId, onClick = { actions.onVehicle(v.id) })
                }
            }
        }
        FwCard(Modifier.fillMaxWidth()) {
            FwOverline(stringResource(R.string.import_preview_ready))
            Text(
                IndianNumberFormat.decimal(plan.entryCount.toDouble(), 0),
                style = FwTheme.type.display,
                color = FwTheme.colors.ink,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                listOf(
                    pluralStringResource(R.plurals.import_count_fuel, plan.refuels.size, plan.refuels.size),
                    pluralStringResource(R.plurals.import_count_expense, plan.expenses.size, plan.expenses.size),
                ).joinToString(" · "),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
            )
        }
        if (plan.duplicates > 0) {
            Note(
                pluralStringResource(R.plurals.import_preview_duplicates, plan.duplicates, plan.duplicates),
                stringResource(R.string.import_preview_duplicates_body),
            )
        }
        if (plan.otherCategoryRows > 0) {
            Note(
                pluralStringResource(R.plurals.import_preview_other, plan.otherCategoryRows, plan.otherCategoryRows),
                stringResource(R.string.import_preview_other_body),
            )
        }
        if (plan.problems.isNotEmpty()) Problems(plan.problems)
    }
}

@Composable
private fun Note(title: String, body: String) {
    FwCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(title, style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium), color = FwTheme.colors.ink)
        Text(body, style = FwTheme.type.bodyS, color = FwTheme.colors.ink2, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Problems(problems: List<RowProblem>) {
    val rows = problems.count { it.issue != RowIssue.TooManyRows }
    FwCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        if (rows > 0) {
            Text(
                pluralStringResource(R.plurals.import_preview_problems, rows, rows),
                style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = FwTheme.colors.negative,
            )
            Text(
                stringResource(R.string.import_preview_problems_body),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        problems.take(MAX_LISTED).forEach { ProblemRow(it) }
        if (problems.size > MAX_LISTED) {
            val more = problems.size - MAX_LISTED
            Text(
                pluralStringResource(R.plurals.import_problems_more, more, more),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ProblemRow(problem: RowProblem) {
    if (problem.issue == RowIssue.TooManyRows) {
        Text(
            pluralStringResource(R.plurals.issue_too_many, ImportPlanner.MAX_ROWS, ImportPlanner.MAX_ROWS),
            style = FwTheme.type.body,
            color = FwTheme.colors.negative,
        )
        return
    }
    val where = problem.at.sheet?.let { stringResource(R.string.import_sheet_row, it, problem.at.row) }
        ?: stringResource(R.string.import_row, problem.at.row)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            where,
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink2,
            modifier = Modifier.weight(ROW_LABEL_WEIGHT),
        )
        Text(
            issueText(problem),
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink,
            modifier = Modifier.weight(ROW_TEXT_WEIGHT),
        )
    }
}

@Composable
private fun issueText(problem: RowProblem): String {
    val value = problem.value.orEmpty()
    return when (problem.issue) {
        RowIssue.VehicleNotFound -> stringResource(R.string.issue_vehicle_not_found, value)

        RowIssue.NoVehicle -> stringResource(R.string.issue_no_vehicle)

        RowIssue.MissingDate -> stringResource(R.string.issue_missing_date)

        RowIssue.BadDate -> stringResource(R.string.issue_bad_date, value)

        RowIssue.MissingOdometer -> stringResource(R.string.issue_missing_odometer)

        RowIssue.BadNumber -> stringResource(R.string.issue_bad_number, value)

        RowIssue.MissingFuelAmounts -> stringResource(R.string.issue_missing_fuel)

        RowIssue.FuelNotOnVehicle -> if (problem.value != null) {
            stringResource(R.string.issue_fuel_not_on_vehicle, value)
        } else {
            stringResource(R.string.issue_tank_not_found)
        }

        RowIssue.MissingAmount -> stringResource(R.string.issue_missing_amount)

        RowIssue.IncomeNotSupported -> stringResource(R.string.issue_income)

        RowIssue.TooManyRows -> pluralStringResource(
            R.plurals.issue_too_many,
            ImportPlanner.MAX_ROWS,
            ImportPlanner.MAX_ROWS,
        )
    }
}

private const val MAX_LISTED = 20
private const val ROW_LABEL_WEIGHT = 0.4f
private const val ROW_TEXT_WEIGHT = 0.6f
