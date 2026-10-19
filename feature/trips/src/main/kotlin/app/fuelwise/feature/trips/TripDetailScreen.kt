package app.fuelwise.feature.trips

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwMetricTile
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwStepper
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.TripCostBasis
import app.fuelwise.core.domain.trip.TripSplit
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.Trip
import app.fuelwise.core.ui.entry.categoryName
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class TripDetailActions(val onPeople: (Int) -> Unit = {}, val onShare: (note: String) -> Unit = {})

@Composable
internal fun TripDetailScreen(links: TripsLinks, viewModel: TripDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var askingUpi by rememberSaveable { mutableStateOf(false) }
    val trip = state.trip
    val title by rememberUpdatedState(trip?.let { tripTitle(it) }.orEmpty())
    val dateText by rememberUpdatedState(trip?.let { IndianNumberFormat.date(localDate(it)) }.orEmpty())
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TripDetailEvent.NeedUpi -> askingUpi = true

                is TripDetailEvent.Share -> {
                    val t = viewModel.state.value.trip ?: return@collect
                    val summary = shareSummary(resources, title, dateText, t, event.split)
                    val message = shareMessage(resources, summary, event.link)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                    try {
                        context.startActivity(Intent.createChooser(send, null))
                    } catch (_: ActivityNotFoundException) {
                        // Nothing on the device can share text; the preview on screen still shows the message.
                    }
                }
            }
        }
    }
    TripDetailContent(state, links, TripDetailActions(viewModel::onPeople, viewModel::share))
    if (askingUpi) {
        UpiSheet(
            state.upiVpa.orEmpty(),
            state.upiName.orEmpty(),
            state.upiError,
            onDismiss = {
                viewModel.clearUpiError()
                askingUpi = false
            },
        ) { vpa, name -> if (viewModel.saveUpiAndShare(vpa, name, title)) askingUpi = false }
    }
}

/** Trip detail (design.md screen 19): stats, what the trip cost and the split with a UPI "pay me" link. */
@Composable
internal fun TripDetailContent(state: TripDetailUiState, links: TripsLinks, actions: TripDetailActions) {
    val trip = state.trip
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.trips_detail_title),
            onBack = links.onBack,
            backContentDescription = stringResource(R.string.trips_back),
            actions = {
                if (trip != null) {
                    IconButton(onClick = { links.onEditTrip(trip.id) }) {
                        Icon(
                            painterResource(FwIcons.Edit),
                            stringResource(R.string.trips_edit),
                            tint = FwTheme.colors.ink,
                        )
                    }
                    IconButton(onClick = { links.onDeleteTrip(trip.id) }) {
                        Icon(
                            painterResource(FwIcons.Delete),
                            stringResource(R.string.trips_delete),
                            tint = FwTheme.colors.ink,
                        )
                    }
                }
            },
        )
        if (state.loading || trip == null) return@Column
        val title = tripTitle(trip)
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter)
                .padding(bottom = FwTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
        ) {
            Column {
                Text(title, style = FwTheme.type.h2, color = FwTheme.colors.ink)
                Text(
                    listOfNotNull(dateLine(trip), purposeName(trip), state.vehicle?.name).joinToString(" · "),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
            }
            Stats(trip, state.split?.total)
            CostsCard(state, trip, links)
            state.split?.let { SplitCard(it, title, trip, actions) }
            trip.notes?.let {
                FwCard {
                    FwOverline(stringResource(R.string.trips_notes))
                    Text(
                        it,
                        style = FwTheme.type.body,
                        color = FwTheme.colors.ink,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stats(trip: Trip, total: Money?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwMetricTile(
            overline = stringResource(R.string.trips_stat_distance),
            value = formatKm(trip.distanceKm),
            unit = stringResource(R.string.trips_km_unit),
            caption = odometerLine(trip),
            modifier = Modifier.weight(1f),
        )
        tripDuration(trip)?.let {
            FwMetricTile(
                overline = stringResource(R.string.trips_stat_duration),
                value = it,
                modifier = Modifier.weight(1f),
            )
        }
        FwMetricTile(
            overline = stringResource(R.string.trips_stat_cost),
            value = total?.let(IndianNumberFormat::rupeesWhole) ?: "—",
            caption = stringResource(R.string.trips_est),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CostsCard(state: TripDetailUiState, trip: Trip, links: TripsLinks) {
    FwCard(contentPadding = PaddingValues(18.dp)) {
        FwOverline(stringResource(R.string.trips_costs), Modifier.padding(bottom = 4.dp))
        trip.estimatedFuelCost?.let { fuel ->
            CostLine(fuelLabel(state.fuelBasis), IndianNumberFormat.rupeesWhole(fuel), onClick = null)
        } ?: Text(
            stringResource(R.string.trips_estimate_none),
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink2,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        state.linked.forEach { cost ->
            val name = cost.category?.let { categoryName(it) }.orEmpty()
            CostLine(
                listOfNotNull(name, cost.expense.tollPlaza).joinToString(" · "),
                IndianNumberFormat.rupeesWhole(cost.expense.amount),
                onClick = { links.onOpenCost(cost.expense.id) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            FwSecondaryButton(
                stringResource(R.string.trips_add_toll),
                { links.onAddCost(trip.vehicleId, trip.id, true) },
                Modifier.weight(1f),
                leadingIcon = FwIcons.Toll,
            )
            FwSecondaryButton(
                stringResource(R.string.trips_add_cost),
                { links.onAddCost(trip.vehicleId, trip.id, false) },
                Modifier.weight(1f),
                leadingIcon = FwIcons.Add,
            )
        }
    }
}

@Composable
private fun CostLine(label: String, amount: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = FwTheme.type.body, color = FwTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(amount, style = FwTheme.type.label, color = FwTheme.colors.ink)
    }
}

@Composable
private fun SplitCard(split: TripSplit, title: String, trip: Trip, actions: TripDetailActions) {
    FwCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FwOverline(stringResource(R.string.trips_split), Modifier.weight(1f))
            FwStepper(
                value = split.people,
                onValueChange = actions.onPeople,
                range = 1..MAX_PEOPLE,
                decreaseDescription = stringResource(R.string.trips_people_fewer),
                increaseDescription = stringResource(R.string.trips_people_more),
                valueDescription = pluralStringResource(R.plurals.trips_people, split.people, split.people),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 12.dp)) {
            Text(IndianNumberFormat.rupeesWhole(split.share), style = FwTheme.type.display, color = FwTheme.colors.ink)
            Text(
                stringResource(R.string.trips_each),
                style = FwTheme.type.body,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
        }
        Text(
            pluralStringResource(
                R.plurals.trips_people_total,
                split.people,
                split.people,
                IndianNumberFormat.rupeesWhole(split.total),
            ),
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink2,
        )
        FwOverline(stringResource(R.string.trips_preview), Modifier.padding(top = 16.dp, bottom = 6.dp))
        Text(
            shareSummary(LocalResources.current, title, IndianNumberFormat.date(localDate(trip)), trip, split),
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink,
            modifier = Modifier.fillMaxWidth()
                .background(FwTheme.colors.surface2, FwTheme.radius.tile)
                .padding(12.dp),
        )
        FwPrimaryButton(
            stringResource(if (split.people > 1) R.string.trips_share_upi else R.string.trips_share),
            { actions.onShare(title) },
            Modifier.fillMaxWidth().padding(top = 14.dp),
            leadingIcon = FwIcons.Share,
        )
        Text(
            stringResource(R.string.trips_never_pays),
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink2,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpiSheet(
    initialVpa: String,
    initialName: String,
    error: Boolean,
    onDismiss: () -> Unit,
    onSave: (vpa: String, name: String) -> Unit,
) {
    var vpa by rememberSaveable { mutableStateOf(initialVpa) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(
            Modifier.padding(horizontal = FwTheme.spacing.gutter).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.m),
        ) {
            Text(stringResource(R.string.trips_upi_title), style = FwTheme.type.h3, color = FwTheme.colors.ink)
            Text(stringResource(R.string.trips_upi_body), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
            FwTextField(
                value = vpa,
                onValueChange = { vpa = it.trim().take(MAX_VPA) },
                label = stringResource(R.string.trips_upi_id),
                placeholder = stringResource(R.string.trips_upi_id_hint),
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                error = if (error) stringResource(R.string.trips_upi_invalid) else null,
            )
            FwTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = stringResource(R.string.trips_upi_name),
                capitalization = KeyboardCapitalization.Words,
            )
            FwInkButton(stringResource(R.string.trips_upi_save), { onSave(vpa, name) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun fuelLabel(basis: TripCostBasis?): String = when (basis) {
    is TripCostBasis.Efficiency -> {
        val unit = stringResource(VehicleLabels.unit(basis.energyType.canonicalUnit))
        stringResource(
            R.string.trips_fuel_efficiency,
            IndianNumberFormat.decimal(basis.quantity, 1),
            unit,
            IndianNumberFormat.decimal(basis.kmPerUnit, 1),
        )
    }

    is TripCostBasis.CostPerKm -> stringResource(R.string.trips_fuel_per_km, IndianNumberFormat.rupees(basis.costPerKm))

    null -> stringResource(R.string.trips_fuel)
}

@Composable
private fun dateLine(trip: Trip): String {
    val zone = ZoneId.of(trip.zoneId)
    val start = Instant.ofEpochMilli(trip.startUtcMs).atZone(zone)
    val end = trip.endUtcMs?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val times = listOfNotNull(start.format(TIME_FORMAT), end?.format(TIME_FORMAT)).joinToString("–")
    return "${IndianNumberFormat.date(start.toLocalDate())} · $times"
}

@Composable
private fun odometerLine(trip: Trip): String? {
    val start = trip.startOdometerKm ?: return null
    val end = trip.endOdometerKm ?: return null
    return stringResource(
        R.string.trips_odometer_range,
        IndianNumberFormat.decimal(start, 0),
        IndianNumberFormat.decimal(end, 0),
    )
}

private fun localDate(trip: Trip) = Instant.ofEpochMilli(trip.startUtcMs).atZone(ZoneId.of(trip.zoneId)).toLocalDate()

/** "Bengaluru → Mysuru, 12 Oct. 146 km, ₹1,236 incl. tolls. Your share is ₹412." */
private fun shareSummary(
    resources: android.content.res.Resources,
    title: String,
    date: String,
    trip: Trip,
    split: TripSplit,
): String {
    val total = IndianNumberFormat.rupeesWhole(split.total)
    val km = formatKm(trip.distanceKm)
    return if (split.people > 1) {
        val share = IndianNumberFormat.rupeesWhole(split.share)
        resources.getString(R.string.trips_share_message, title, date, km, total, share)
    } else {
        resources.getString(R.string.trips_share_message_solo, title, date, km, total)
    }
}

private fun shareMessage(resources: android.content.res.Resources, summary: String, link: String?): String =
    if (link == null) summary else summary + "\n" + resources.getString(R.string.trips_share_pay, link)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private const val MAX_PEOPLE = 20
private const val MAX_VPA = 80
private const val MAX_NAME = 40
