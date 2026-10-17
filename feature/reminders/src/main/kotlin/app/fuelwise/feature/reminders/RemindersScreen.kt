package app.fuelwise.feature.reminders

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.reminder.ReminderStatus
import app.fuelwise.core.domain.reminder.Urgency
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
internal fun RemindersScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(viewModel.canNotify()) }
    // Re-check when returning from the system settings screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { canNotify = viewModel.canNotify() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify =
            viewModel.canNotify()
    }
    RemindersContent(
        state = state,
        canNotify = canNotify,
        onBack = onBack,
        onAdd = onAdd,
        onOpen = onOpen,
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.canNotify()) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}

/** Reminders (design.md screen 23): Upcoming grouped by vehicle and coloured by urgency, and Done. */
@Composable
internal fun RemindersContent(
    state: RemindersUiState,
    canNotify: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onEnableNotifications: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.reminders_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.reminders_back),
            actions = {
                IconButton(onClick = onAdd) {
                    Icon(
                        painterResource(FwIcons.Add),
                        stringResource(R.string.reminders_add),
                        tint = FwTheme.colors.ink,
                    )
                }
            },
        )
        FwSegmentedControl(
            options = listOf(stringResource(R.string.reminders_upcoming), stringResource(R.string.reminders_done)),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
        )
        if (!canNotify && state.upcoming.isNotEmpty()) NotificationsBanner(onEnableNotifications)
        if (state.loading) return@Column
        if (tab == 0) Upcoming(state, onAdd, onOpen) else Done(state)
    }
}

@Composable
private fun NotificationsBanner(onEnable: () -> Unit) {
    FwCard(
        Modifier.padding(horizontal = FwTheme.spacing.gutter, vertical = 4.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            stringResource(R.string.reminders_notifications_off),
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink,
        )
        FwTextButton(stringResource(R.string.reminders_notifications_turn_on), onEnable)
    }
}

@Composable
private fun Upcoming(state: RemindersUiState, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    if (state.upcoming.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FwEmptyState(
                icon = FwIcons.Schedule,
                headline = stringResource(R.string.reminders_empty_title),
                body = stringResource(R.string.reminders_empty_body),
                actionLabel = stringResource(R.string.reminders_add),
                onAction = onAdd,
            )
        }
        return
    }
    val names = state.vehicles.associate { it.id to it.name }
    val grouped = state.upcoming.groupBy { it.reminder.vehicleId }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
    ) {
        grouped.forEach { (vehicleId, list) ->
            if (grouped.size > 1) {
                item(key = "h-$vehicleId") {
                    Text(
                        names[vehicleId].orEmpty(),
                        style = FwTheme.type.label,
                        color = FwTheme.colors.ink2,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
            }
            items(list, key = { it.reminder.id }) { ReminderRow(it) { onOpen(it.reminder.id) } }
        }
    }
}

@Composable
internal fun ReminderRow(status: ReminderStatus, onClick: () -> Unit) {
    val r = status.reminder
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(FwTheme.colors.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(CareLabels.reminderIcon(r.kind)),
                null,
                tint = FwTheme.colors.ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                r.title ?: stringResource(CareLabels.reminder(r.kind)),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink,
            )
            Text(dueText(status), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
        }
        UrgencyPill(status.urgency)
    }
}

@Composable
private fun UrgencyPill(urgency: Urgency) {
    val (label, color) = when (urgency) {
        Urgency.Overdue -> R.string.urgency_overdue to FwTheme.colors.negative
        Urgency.Due -> R.string.urgency_due to FwTheme.colors.warning
        Urgency.Soon -> R.string.urgency_soon to FwTheme.colors.ink2
        Urgency.Later -> R.string.urgency_later to FwTheme.colors.ink3
    }
    Text(
        stringResource(label),
        style = FwTheme.type.caption,
        color = color,
        modifier = Modifier.background(
            color.copy(alpha = PILL_ALPHA),
            FwTheme.radius.pill,
        ).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** "in 12 days · 16 Nov 2026", "in ~420 km", "3 days overdue". Whichever comes first leads. */
@Composable
internal fun dueText(status: ReminderStatus): String {
    val parts = buildList {
        status.daysLeft?.let { days ->
            val date = status.reminder.dueDateUtcMs?.let {
                IndianNumberFormat.date(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
            }
            add(
                when {
                    days < 0 -> pluralStringResource(R.plurals.due_days_overdue, abs(days).toInt(), abs(days).toInt())
                    days == 0L -> stringResource(R.string.due_today)
                    else -> pluralStringResource(R.plurals.due_in_days, days.toInt(), days.toInt())
                } + (date?.let { " · $it" } ?: ""),
            )
        }
        status.kmLeft?.let { km ->
            val n = IndianNumberFormat.group(abs(km).roundToLong())
            add(if (km < 0) stringResource(R.string.due_km_overdue, n) else stringResource(R.string.due_in_km, n))
        }
        if (status.daysLeft == null && status.kmLeft == null) {
            status.reminder.dueOdometerKm?.let {
                add(stringResource(R.string.due_at_km, IndianNumberFormat.group(it.roundToLong())))
            }
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun Done(state: RemindersUiState) {
    if (state.done.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.reminders_done_empty), style = FwTheme.type.body, color = FwTheme.colors.ink2)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
    ) {
        items(state.done, key = { it.id }) { c ->
            val reminder = state.titles[c.reminderId]
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(FwIcons.CheckCircleFilled),
                    null,
                    tint = FwTheme.colors.positive,
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        reminder?.let { it.title ?: stringResource(CareLabels.reminder(it.kind)) }
                            ?: stringResource(R.string.reminders_completed),
                        style = FwTheme.type.body,
                        color = FwTheme.colors.ink,
                    )
                    Text(
                        listOfNotNull(
                            IndianNumberFormat.date(
                                Instant.ofEpochMilli(c.completedAtUtcMs).atZone(ZoneId.systemDefault()).toLocalDate(),
                            ),
                            c.odometerKm?.let { "${IndianNumberFormat.group(it.roundToLong())} km" },
                            state.vehicles.firstOrNull { it.id == c.vehicleId }?.name,
                        ).joinToString(" · "),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink2,
                    )
                }
            }
        }
    }
}

private const val PILL_ALPHA = 0.12f
