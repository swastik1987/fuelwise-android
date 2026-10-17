package app.fuelwise.feature.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.care.CareLabels
import app.fuelwise.core.ui.format.IndianNumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
internal fun DocumentsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DocumentsContent(state, onBack, onAdd, onOpen)
}

/** Documents vault (design.md screen 24): card per document with its expiry status. */
@Composable
internal fun DocumentsContent(
    state: DocumentsUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.documents_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.documents_back),
            actions = {
                IconButton(onClick = onAdd) {
                    Icon(
                        painterResource(FwIcons.Add),
                        stringResource(R.string.documents_add),
                        tint = FwTheme.colors.ink,
                    )
                }
            },
        )
        if (state.loading) return@Column
        if (state.documents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FwEmptyState(
                    icon = FwIcons.Document,
                    headline = stringResource(R.string.documents_empty_title),
                    body = stringResource(R.string.documents_empty_body),
                    actionLabel = stringResource(R.string.documents_add),
                    onAction = onAdd,
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.documents_private_note),
                    style = FwTheme.type.caption,
                    color = FwTheme.colors.ink2,
                )
            }
            items(state.documents, key = { it.id }) { doc ->
                DocumentCard(doc, doc.vehicleId?.let { state.vehicles[it] }, state.nowUtcMs) { onOpen(doc.id) }
            }
        }
    }
}

@Composable
internal fun DocumentCard(doc: Document, vehicle: Vehicle?, nowUtcMs: Long, onClick: () -> Unit) {
    FwCard(onClick = onClick, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(CareLabels.documentIcon(doc.type)),
                null,
                tint = FwTheme.colors.ink,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(CareLabels.document(doc.type)),
                style = FwTheme.type.label,
                color = FwTheme.colors.ink,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            ExpiryChip(doc.expiryDateUtcMs, nowUtcMs)
        }
        doc.number?.let {
            Text(it, style = FwTheme.type.mono, color = FwTheme.colors.ink, modifier = Modifier.padding(top = 14.dp))
        }
        Text(
            listOfNotNull(vehicle?.name, doc.issuer ?: doc.insurance?.insurer).joinToString(" · "),
            style = FwTheme.type.caption,
            color = FwTheme.colors.ink2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun ExpiryChip(expiryUtcMs: Long?, nowUtcMs: Long) {
    val expiry = expiryUtcMs ?: return
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowUtcMs).atZone(zone).toLocalDate(), expiry.toDate(zone))
    val (text, color) = when {
        days < 0 -> stringResource(R.string.expiry_expired) to FwTheme.colors.negative

        days <= EXPIRING_DAYS -> pluralStringResource(R.plurals.expiry_in_days, days.toInt(), days.toInt()) to
            FwTheme.colors.warning

        else -> stringResource(R.string.expiry_valid_until, IndianNumberFormat.date(expiry.toDate(zone))) to
            FwTheme.colors.positive
    }
    Text(
        text,
        style = FwTheme.type.caption,
        color = color,
        modifier = Modifier.background(
            color.copy(alpha = CHIP_ALPHA),
            FwTheme.radius.pill,
        ).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun DocumentViewScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: DocumentViewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DocumentViewContent(state, attachments, onBack, onEdit, onOpenApp = { context.openApp(it) })
}

/**
 * Quick view (design.md screen 24): a large card to show at a checkpoint, with the reminder that legally valid
 * digital documents are the ones in DigiLocker or mParivahan (PRD F-12).
 */
@Composable
internal fun DocumentViewContent(
    state: DocumentViewState,
    attachments: List<AttachmentPreview>,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val doc = state.document
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = doc?.let { stringResource(CareLabels.document(it.type)) }.orEmpty(),
            onBack = onBack,
            backContentDescription = stringResource(R.string.documents_back),
            actions = {
                if (doc != null) {
                    IconButton(onClick = { onEdit(doc.id) }) {
                        Icon(
                            painterResource(FwIcons.Edit),
                            stringResource(R.string.documents_edit),
                            tint = FwTheme.colors.ink,
                        )
                    }
                }
            },
        )
        if (doc == null) return@Column
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.l),
        ) {
            FwCard(contentPadding = PaddingValues(24.dp)) {
                FwOverline(
                    listOfNotNull(
                        stringResource(CareLabels.document(doc.type)),
                        state.vehicle?.name,
                    ).joinToString(" · "),
                )
                Text(
                    doc.number ?: "—",
                    style = FwTheme.type.mono.copy(
                        fontSize = FwTheme.type.display.fontSize,
                        lineHeight = FwTheme.type.display.lineHeight,
                    ),
                    color = FwTheme.colors.ink,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                doc.expiryDateUtcMs?.let {
                    Text(
                        stringResource(
                            R.string.view_expires,
                            IndianNumberFormat.date(it.toDate(ZoneId.systemDefault())),
                        ),
                        style = FwTheme.type.h3,
                        color = FwTheme.colors.ink,
                    )
                }
                ExpiryChip(doc.expiryDateUtcMs, state.nowUtcMs)
                (doc.issuer ?: doc.insurance?.insurer)?.let {
                    Text(
                        it,
                        style = FwTheme.type.bodyS,
                        color = FwTheme.colors.ink2,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            attachments.forEach { preview ->
                preview.image?.let { image ->
                    Image(
                        image,
                        contentDescription = stringResource(R.string.view_attachment),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(FwTheme.radius.card),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.view_legal_note), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FwSecondaryButton(stringResource(R.string.view_open_digilocker), {
                        onOpenApp(DIGILOCKER)
                    }, Modifier.weight(1f))
                    FwSecondaryButton(stringResource(R.string.view_open_mparivahan), {
                        onOpenApp(MPARIVAHAN)
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Opens the app if installed, otherwise its Play Store page. Nothing is sent anywhere by us. */
private fun Context.openApp(packageName: String) {
    val launch = packageManager.getLaunchIntentForPackage(packageName)
    val intent = launch ?: Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun Long.toDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private const val EXPIRING_DAYS = 30L
private const val CHIP_ALPHA = 0.12f
internal const val DIGILOCKER = "com.digilocker.android"
internal const val MPARIVAHAN = "com.nic.mparivahan"
