package app.fuelwise.feature.insights

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwLogoMark
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.insights.Recap
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
internal fun RecapScreen(onBack: () -> Unit, viewModel: RecapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
            if (uri != null) scope.launch { viewModel.save(layer.toImageBitmap().asAndroidBitmap(), uri) }
        }
    val shareTitle = stringResource(R.string.recap_share)
    val savedText = stringResource(R.string.recap_saved)
    val failedText = stringResource(R.string.recap_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecapEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, event.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }

                RecapEvent.Saved -> Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show()

                RecapEvent.Failed -> Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            }
        }
    }
    RecapContent(
        state = state,
        layer = layer,
        onBack = onBack,
        onShare = { scope.launch { viewModel.share(layer.toImageBitmap().asAndroidBitmap()) } },
        onSave = { saveLauncher.launch(viewModel.fileName) },
        onShowVehicleName = viewModel::setShowVehicleName,
    )
}

/** Monthly recap (design.md screen 16): the poster, share and save, and the vehicle-name opt-in. */
@Composable
internal fun RecapContent(
    state: RecapUiState?,
    layer: GraphicsLayer?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onShowVehicleName: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.recap_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.insights_back),
        )
        if (state == null) return@Column
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.l),
        ) {
            val captured = if (layer == null) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
            }
            Box(
                Modifier.fillMaxWidth(
                    POSTER_WIDTH,
                ).align(androidx.compose.ui.Alignment.CenterHorizontally).then(captured),
            ) {
                RecapPoster(state.recap, state.vehicle.name.takeIf { state.showVehicleName })
            }
            FwSwitchRow(
                title = stringResource(R.string.recap_show_vehicle),
                checked = state.showVehicleName,
                onCheckedChange = onShowVehicleName,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwInkButton(stringResource(R.string.recap_share), onShare, Modifier.weight(1f))
                FwSecondaryButton(stringResource(R.string.recap_save), onSave, Modifier.weight(1f))
            }
        }
    }
}

/** Bold typographic 9:16 poster. Always light so a shared image looks the same everywhere. */
@Composable
internal fun RecapPoster(recap: Recap, vehicleName: String?) {
    FuelwiseTheme(darkTheme = false) { PosterBody(recap, vehicleName) }
}

@Composable
private fun PosterBody(recap: Recap, vehicleName: String?) {
    val ink = FwTheme.colors.ink
    val stripe = recap.energyType?.let { VehicleLabels.energyColor(it) } ?: FwTheme.colors.accent
    val unit = recap.energyType?.let { stringResource(VehicleLabels.unit(it.canonicalUnit)) }.orEmpty()
    Row(
        Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO).clip(FwTheme.radius.card).background(FwTheme.colors.surface),
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(stripe))
        Column(Modifier.padding(24.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // One line whatever the month: "September" shrinks to fit rather than breaking mid-word.
            BasicText(
                recap.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                style = FwTheme.type.displayXl.copy(color = ink),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 56.sp),
            )
            Text(
                listOfNotNull(vehicleName, recap.month.year.toString()).joinToString(" · "),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
            )
            Spacer(Modifier.weight(1f))
            PosterFigure(
                recap.distanceKm?.let {
                    "${IndianNumberFormat.decimal(it, 0)} km"
                } ?: "—",
                stringResource(R.string.recap_driven),
            )
            PosterFigure(IndianNumberFormat.rupeesWhole(recap.spend), stringResource(R.string.recap_spent))
            recap.costPerKm?.let {
                PosterFigure(IndianNumberFormat.rupees(it) + "/km", stringResource(R.string.recap_cost_per_km))
            }
            recap.bestTank?.let {
                PosterFigure(
                    "${IndianNumberFormat.decimal(it.kmPerUnit, 1)} km/$unit",
                    stringResource(R.string.recap_best_tank),
                )
            }
            recap.costPerKmChange?.let { change ->
                val pct = (abs(change) * PERCENT).roundToInt()
                Text(
                    stringResource(if (change <= 0) R.string.recap_cheaper else R.string.recap_dearer, pct),
                    style = FwTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = ink,
                )
            }
            Spacer(Modifier.weight(1f))
            FwLogoMark(size = 28.dp)
        }
    }
}

@Composable
private fun PosterFigure(value: String, label: String) {
    Column {
        Text(value, style = FwTheme.type.h1, color = FwTheme.colors.ink)
        Text(label, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
    }
}

private const val POSTER_RATIO = 9f / 16f
private const val POSTER_WIDTH = 0.82f
private const val PERCENT = 100
