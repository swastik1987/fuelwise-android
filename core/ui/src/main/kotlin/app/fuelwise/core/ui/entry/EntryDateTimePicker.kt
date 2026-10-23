package app.fuelwise.core.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Date then time, for back-dating an entry. Future dates are not selectable. Returns UTC ms; the caller stores the
 * zone separately (CLAUDE.md: UTC ms plus zoneId).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDateTimePicker(initialUtcMs: Long, zone: ZoneId, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val initial = Instant.ofEpochMilli(initialUtcMs).atZone(zone)
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now(zone)
    if (pickedDate == null) {
        // DatePicker works in UTC midnights; convert the local date both ways.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    pickedDate =
                        dateState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                            ?: initial.toLocalDate()
                }) { Text(stringResource(R.string.picker_next), color = FwTheme.colors.ink) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.picker_cancel), color = FwTheme.colors.ink)
                }
            },
        ) { DatePicker(state = dateState) }
    } else {
        val timeState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        Dialog(onDismissRequest = onDismiss) {
            Column(Modifier.background(FwTheme.colors.surface, FwTheme.radius.card).padding(24.dp)) {
                TimePicker(state = timeState)
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.picker_cancel), color = FwTheme.colors.ink)
                    }
                    TextButton(onClick = {
                        val local = pickedDate!!.atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone)
                        onPicked(minOf(local.toInstant().toEpochMilli(), System.currentTimeMillis()))
                    }) { Text(stringResource(R.string.picker_done), color = FwTheme.colors.ink) }
                }
            }
        }
    }
}

/** Date only, any date allowed: due dates and expiries (reminders, documents, recurrences). Returns local midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDatePicker(initialUtcMs: Long, zone: ZoneId, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val initial = Instant.ofEpochMilli(initialUtcMs).atZone(zone).toLocalDate()
    // DatePicker works in UTC midnights; convert the local date both ways.
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val date =
                    state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: initial
                onPicked(date.atStartOfDay(zone).toInstant().toEpochMilli())
            }) { Text(stringResource(R.string.picker_done), color = FwTheme.colors.ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel), color = FwTheme.colors.ink) }
        },
    ) { DatePicker(state = state) }
}

/** A from–to date range (Logbook filter, PRD F-05). Hands back both days, or nothing if the user hasn't picked two. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDateRangePicker(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onPicked: (from: LocalDate, to: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DateRangePicker works in UTC midnights, like DatePicker.
    fun LocalDate.utc() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.utc(),
        initialSelectedEndDateMillis = initialTo?.utc(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = state.selectedStartDateMillis?.date()
                    val to = state.selectedEndDateMillis?.date() ?: from
                    if (from != null && to != null) onPicked(from, to) else onDismiss()
                },
            ) { Text(stringResource(R.string.picker_done), color = FwTheme.colors.ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel), color = FwTheme.colors.ink) }
        },
    ) { DateRangePicker(state = state, modifier = Modifier.weight(1f)) }
}
