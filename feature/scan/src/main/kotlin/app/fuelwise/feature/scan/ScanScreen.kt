package app.fuelwise.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCheckboxRow
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.scan.GuideRegion
import app.fuelwise.core.model.QuantityUnit
import app.fuelwise.core.model.ScanPrefill
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun ScanScreen(
    onBack: () -> Unit,
    onResult: (ScanPrefill) -> Unit,
    onTypeInstead: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onCameraAccess(granted)
    }
    // Asked here, when the user has just tapped a scan action (PRD §9), never at app start.
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onCameraAccess(true) else permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ScanViewModel.Event.Done -> onResult(event.prefill)
            }
        }
    }
    val camera = remember { ScanCamera() }
    ScanContent(
        state = state,
        actions = ScanActions(
            onSnap = {
                viewModel.onSnapStarted()
                camera.snap(viewModel::onSnap)
            },
            onGuidePlaced = { camera.guide = it },
            onBack = onBack,
            onTypeInstead = onTypeInstead,
            onAllowCamera = { permission.launch(Manifest.permission.CAMERA) },
            onMode = viewModel::onMode,
            onReadNow = viewModel::onReadNow,
            onToggleField = viewModel::onToggleField,
            onAttachPhoto = viewModel::onAttachPhoto,
            onRescan = viewModel::onRescan,
            onUse = viewModel::onUse,
        ),
        viewfinder = {
            ScanCameraPreview(camera = camera, onFrame = viewModel::onFrame, modifier = Modifier.fillMaxSize())
        },
    )
}

/** Callbacks for [ScanContent]; defaults make screenshot tests easy. */
internal data class ScanActions(
    val onBack: () -> Unit = {},
    val onTypeInstead: () -> Unit = {},
    val onAllowCamera: () -> Unit = {},
    val onMode: (ScanMode) -> Unit = {},
    val onReadNow: () -> Unit = {},
    val onSnap: () -> Unit = {},
    /** Where the guide sits over the camera view, so only the display under it is read. */
    val onGuidePlaced: (GuideRegion) -> Unit = {},
    val onToggleField: (ScanField) -> Unit = {},
    val onAttachPhoto: (Boolean) -> Unit = {},
    val onRescan: () -> Unit = {},
    val onUse: () -> Unit = {},
)

/** design.md screen 9. [viewfinder] is the live camera; tests pass a plain surface. */
@Composable
internal fun ScanContent(state: ScanUiState, actions: ScanActions, viewfinder: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        when (state.camera) {
            CameraAccess.Denied -> DeniedState(actions)
            CameraAccess.Unknown -> Unit
            CameraAccess.Granted -> CameraState(state, actions, viewfinder)
        }
        FwTopBar(
            title = stringResource(R.string.scan_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.scan_close),
        )
        state.review?.let { ReviewSheet(it, state, actions, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun DeniedState(actions: ScanActions) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FwEmptyState(
            icon = FwIcons.Camera,
            headline = stringResource(R.string.scan_denied_title),
            body = stringResource(R.string.scan_denied_body),
            actionLabel = stringResource(R.string.scan_allow),
            onAction = actions.onAllowCamera,
        )
        FwTextButton(stringResource(R.string.scan_type_instead), actions.onTypeInstead)
    }
}

@Composable
private fun CameraState(state: ScanUiState, actions: ScanActions, viewfinder: @Composable () -> Unit) {
    var area by remember { mutableStateOf<Rect?>(null) }
    var frame by remember { mutableStateOf<Rect?>(null) }
    val onGuidePlaced by rememberUpdatedState(actions.onGuidePlaced)
    LaunchedEffect(area, frame) {
        val a = area ?: return@LaunchedEffect
        val g = frame ?: return@LaunchedEffect
        if (a.width <= 0f || a.height <= 0f) return@LaunchedEffect
        onGuidePlaced(
            GuideRegion(
                left = (g.left - a.left) / a.width,
                top = (g.top - a.top) / a.height,
                right = (g.right - a.left) / a.width,
                bottom = (g.bottom - a.top) / a.height,
                viewAspect = a.width / a.height,
            ),
        )
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { area = it.boundsInRoot() }) {
        viewfinder()
        LiveLabels(state)
        Column(Modifier.fillMaxSize()) {
            // Room for the top bar drawn over the camera.
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(FwTheme.size.topBarHeight))
            Guide(
                state,
                onFramePlaced = { frame = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = FwTheme.spacing.xxl, vertical = FwTheme.spacing.s),
            )
            Controls(state, actions)
        }
    }
}

@Composable
private fun Controls(state: ScanUiState, actions: ScanActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(FwTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
    ) {
        if (state.struggling && state.review == null) Struggling(actions)
        FwSegmentedControl(
            options = ScanMode.entries.map { stringResource(it.label()) },
            selectedIndex = state.mode.ordinal,
            onSelect = { actions.onMode(ScanMode.entries[it]) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.s)) {
            FwPrimaryButton(
                stringResource(R.string.scan_read_now),
                actions.onReadNow,
                modifier = Modifier.weight(1f),
                enabled = !state.snapping,
            )
            val snapLabel = stringResource(if (state.snapping) R.string.scan_snapping else R.string.scan_snap)
            val snapDescription = stringResource(R.string.scan_snap_cd)
            FwSecondaryButton(
                snapLabel,
                actions.onSnap,
                modifier = Modifier
                    .weight(1f)
                    .semantics { if (!state.snapping) contentDescription = snapDescription },
                enabled = !state.snapping,
                leadingIcon = FwIcons.Camera,
            )
        }
    }
}

/**
 * The rounded guide the user fits the display into, with a check once all values agree. Only what's under it is
 * read. For a pump meter it's shaped like an Indian dispenser display: amount and quantity stacked at the top,
 * density in between, rate at the bottom. [onFramePlaced] gets the frame's bounds in the root.
 */
@Composable
private fun Guide(state: ScanUiState, onFramePlaced: (Rect) -> Unit, modifier: Modifier) {
    val colors = FwTheme.colors
    val ratio = when (state.mode) {
        ScanMode.PumpMeter -> PUMP_RATIO
        ScanMode.Receipt -> RECEIPT_RATIO
        ScanMode.Odometer -> ODOMETER_RATIO
    }
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(state.mode.guide()),
            style = FwTheme.type.label,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = FwTheme.spacing.s)
                .background(colors.surface, FwTheme.radius.pill)
                .padding(horizontal = FwTheme.spacing.s, vertical = FwTheme.spacing.xxs),
        )
        // Not filled: the frame takes the height its shape needs, so the hint stays just above it.
        Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .onGloballyPositioned { onFramePlaced(it.boundsInRoot()) }
                    .border(2.dp, if (state.allValid) colors.positive else colors.onAccent, FwTheme.radius.card),
            ) {
                if (state.mode == ScanMode.PumpMeter) PumpBands(state.unit)
                GuideCheck(state)
            }
        }
    }
}

/** Where each value sits on a typical dispenser; a hint for framing, the reading doesn't depend on it. */
@Composable
private fun PumpBands(unit: QuantityUnit) {
    val unitText = stringResource(VehicleLabels.unit(unit))
    Column(Modifier.fillMaxSize().padding(FwTheme.spacing.s)) {
        BandLabel(stringResource(R.string.scan_field_total), Modifier.weight(AMOUNT_BAND))
        BandLabel(
            stringResource(R.string.scan_band_quantity, stringResource(R.string.scan_field_quantity), unitText),
            Modifier.weight(QUANTITY_BAND),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(FwTheme.size.hairline)
                .background(FwTheme.colors.onAccent),
        )
        Spacer(Modifier.weight(DENSITY_BAND))
        BandLabel(
            stringResource(R.string.scan_band_rate, stringResource(R.string.scan_field_rate), unitText),
            Modifier.weight(RATE_BAND),
        )
    }
}

@Composable
private fun BandLabel(text: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth()) {
        Text(
            text,
            style = FwTheme.type.label,
            color = FwTheme.colors.ink2,
            modifier = Modifier
                .background(FwTheme.colors.surface, FwTheme.radius.pill)
                .padding(horizontal = FwTheme.spacing.xs, vertical = FwTheme.spacing.xxs)
                .clearAndSetSemantics {},
        )
    }
}

@Composable
private fun BoxScope.GuideCheck(state: ScanUiState) {
    if (!state.allValid) return
    Icon(
        painterResource(FwIcons.CheckCircleFilled),
        contentDescription = stringResource(R.string.scan_all_valid_cd),
        tint = FwTheme.colors.positive,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(FwTheme.spacing.s)
            .size(FwTheme.size.iconTile)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Values pinned where they were read, mapped from image pixels to the fill-centre preview. */
@Composable
private fun LiveLabels(state: ScanUiState) {
    if (state.frameWidth <= 0 || state.frameHeight <= 0) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val scale = max(viewW / state.frameWidth, viewH / state.frameHeight)
        val dx = (viewW - state.frameWidth * scale) / 2
        val dy = (viewH - state.frameHeight * scale) / 2
        state.labels.forEach { label ->
            val text = formatValue(label.field, state.live, state.unit) ?: return@forEach
            Text(
                text,
                style = FwTheme.type.monoS,
                color = FwTheme.colors.ink,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (label.box.left * scale + dx).roundToInt(),
                            (label.box.top * scale + dy).roundToInt() - LABEL_LIFT_PX,
                        )
                    }
                    .background(FwTheme.colors.surface, FwTheme.radius.tile)
                    .padding(horizontal = FwTheme.spacing.xs, vertical = FwTheme.spacing.xxs),
            )
        }
    }
}

@Composable
private fun Struggling(actions: ScanActions) {
    Surface(color = FwTheme.colors.surface, shape = FwTheme.radius.card) {
        Column(
            Modifier.padding(FwTheme.spacing.m).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
        ) {
            Text(stringResource(R.string.scan_struggling), style = FwTheme.type.body, color = FwTheme.colors.ink)
            FwInkButton(stringResource(R.string.scan_type_instead), actions.onTypeInstead)
        }
    }
}

/** The confirm step: every value is shown, and only ticked ones go to Add fuel. */
@Composable
private fun ReviewSheet(review: ScanReview, state: ScanUiState, actions: ScanActions, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), color = FwTheme.colors.surface, shape = FwTheme.radius.sheet) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(FwTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
        ) {
            val title = when {
                state.mode == ScanMode.Odometer -> R.string.scan_review_odometer
                review.validated -> R.string.scan_review_validated
                else -> R.string.scan_review_check
            }
            Text(stringResource(title), style = FwTheme.type.h3, color = FwTheme.colors.ink)
            if (!review.validated && state.mode != ScanMode.Odometer) {
                Text(
                    stringResource(R.string.scan_review_check_hint),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
            }
            review.fields.forEach { field ->
                FwCheckboxRow(
                    title = formatValue(field, review.values, state.unit).orEmpty(),
                    checked = field in review.confirmed,
                    onCheckedChange = { actions.onToggleField(field) },
                    subtitle = stringResource(field.label()),
                )
            }
            if (review.hasPhoto) {
                FwSwitchRow(
                    title = stringResource(R.string.scan_attach_photo),
                    checked = state.attachPhoto,
                    onCheckedChange = actions.onAttachPhoto,
                    subtitle = stringResource(R.string.scan_attach_photo_hint),
                )
            }
            FwPrimaryButton(
                stringResource(R.string.scan_use),
                actions.onUse,
                enabled = review.confirmed.isNotEmpty(),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FwTextButton(stringResource(R.string.scan_rescan), actions.onRescan)
            }
        }
    }
}

@Composable
private fun formatValue(field: ScanField, values: ScanPrefill, unit: QuantityUnit): String? {
    val unitText = stringResource(VehicleLabels.unit(unit))
    return when (field) {
        ScanField.Total -> values.total?.let(IndianNumberFormat::rupees)

        ScanField.Quantity -> values.quantity?.let { IndianNumberFormat.decimal(it, 2) + " " + unitText }

        ScanField.Rate -> values.unitPrice?.let {
            stringResource(R.string.scan_rate, IndianNumberFormat.rupees(it), unitText)
        }

        ScanField.Odometer -> values.odometerKm?.let { stringResource(R.string.scan_km, IndianNumberFormat.group(it)) }

        ScanField.Date -> values.dateTimeUtcMs?.let {
            IndianNumberFormat.date(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
        }

        ScanField.Station -> values.stationName
    }
}

private fun ScanMode.label(): Int = when (this) {
    ScanMode.PumpMeter -> R.string.scan_mode_pump
    ScanMode.Receipt -> R.string.scan_mode_receipt
    ScanMode.Odometer -> R.string.scan_mode_odometer
}

private fun ScanMode.guide(): Int = when (this) {
    ScanMode.PumpMeter -> R.string.scan_guide_pump
    ScanMode.Receipt -> R.string.scan_guide_receipt
    ScanMode.Odometer -> R.string.scan_guide_odometer
}

private fun ScanField.label(): Int = when (this) {
    ScanField.Total -> R.string.scan_field_total
    ScanField.Quantity -> R.string.scan_field_quantity
    ScanField.Rate -> R.string.scan_field_rate
    ScanField.Odometer -> R.string.scan_field_odometer
    ScanField.Date -> R.string.scan_field_date
    ScanField.Station -> R.string.scan_field_station
}

// Guide width ÷ height. A dispenser display is about as tall as it is wide; an odometer is a wide strip.
private const val PUMP_RATIO = 0.85f
private const val ODOMETER_RATIO = 1.4f

// Pump display bands, top to bottom: amount, quantity, density (not read), rate.
private const val AMOUNT_BAND = 0.3f
private const val QUANTITY_BAND = 0.25f
private const val DENSITY_BAND = 0.2f
private const val RATE_BAND = 0.25f
private const val RECEIPT_RATIO = 0.7f
private const val LABEL_LIFT_PX = 48
