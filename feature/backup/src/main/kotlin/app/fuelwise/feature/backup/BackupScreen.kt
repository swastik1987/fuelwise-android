package app.fuelwise.feature.backup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwSegmentedControl
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.BackupError
import app.fuelwise.core.model.BackupFrequency
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.sync.drive.RemoteBackup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the screen can ask for; the route wires these to the view model and system pickers. */
internal data class BackupActions(
    val onBack: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onFrequency: (BackupFrequency) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onCharging: (Boolean) -> Unit = {},
    val onEnableEncryption: (CharArray) -> Unit = {},
    val onDisableEncryption: () -> Unit = {},
    val onBackUpNow: () -> Unit = {},
    val onShowBackups: () -> Unit = {},
    val onCloseBackups: () -> Unit = {},
    val onPickBackup: (RemoteBackup) -> Unit = {},
    val onConfirmRestore: (CharArray?) -> Unit = {},
    val onCancelRestore: () -> Unit = {},
    val onSaveFile: () -> Unit = {},
    val onOpenFile: () -> Unit = {},
)

@Composable
internal fun BackupScreen(onBack: () -> Unit, onRestored: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(it.data)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::pickFile)
        }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is BackupEvent.Consent -> {
                    consent.launch(IntentSenderRequest.Builder(event.sender).build())
                    null
                }

                is BackupEvent.BackedUp -> resources.getString(R.string.backup_done_toast)

                is BackupEvent.Failed -> resources.getString(errorText(event.error))

                is BackupEvent.Restored -> {
                    onRestored()
                    resources.getQuantityString(R.plurals.backup_restored_toast, event.entries, event.entries)
                }

                BackupEvent.Exported -> resources.getString(R.string.backup_exported_toast)
            }
            message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
    BackupContent(
        state,
        BackupActions(
            onBack = onBack,
            onConnect = { viewModel.connect(context) },
            onDisconnect = viewModel::disconnect,
            onFrequency = viewModel::setFrequency,
            onWifiOnly = viewModel::setWifiOnly,
            onCharging = viewModel::setChargingOnly,
            onEnableEncryption = viewModel::enableEncryption,
            onDisableEncryption = viewModel::disableEncryption,
            onBackUpNow = viewModel::backUpNow,
            onShowBackups = viewModel::loadBackups,
            onCloseBackups = viewModel::closeBackups,
            onPickBackup = viewModel::askRestore,
            onConfirmRestore = viewModel::restore,
            onCancelRestore = viewModel::cancelRestore,
            onSaveFile = { save.launch(viewModel.exportFileName) },
            onOpenFile = { open.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) },
        ),
    )
}

/** Backup & sync (design.md screen 27). */
@Composable
internal fun BackupContent(state: BackupUiState, actions: BackupActions) {
    var settingPassphrase by rememberSaveable { mutableStateOf(false) }
    var confirmDisable by rememberSaveable { mutableStateOf(false) }
    val s = state.settings
    val busy = state.busy != null
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.backup_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.backup_back),
        )
        Column(
            Modifier.verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = FwTheme.spacing.gutter, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.l),
        ) {
            StatusHero(state)
            if (!state.configured) {
                Note(stringResource(R.string.backup_not_configured))
            } else if (!s.isSignedIn) {
                Text(
                    stringResource(R.string.backup_connect_body),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
                FwInkButton(
                    stringResource(R.string.backup_connect),
                    actions.onConnect,
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
            s.lastError?.let { error -> ErrorBanner(error, actions) }
            if (s.isSignedIn) {
                FwOverline(stringResource(R.string.backup_frequency))
                val options = BackupFrequency.entries
                FwSegmentedControl(
                    options = options.map { stringResource(frequencyLabel(it)) },
                    selectedIndex = options.indexOf(s.frequency),
                    onSelect = { actions.onFrequency(options[it]) },
                )
                FwSwitchRow(
                    title = stringResource(R.string.backup_wifi_only),
                    checked = s.wifiOnly,
                    onCheckedChange = actions.onWifiOnly,
                )
                FwSwitchRow(
                    title = stringResource(R.string.backup_charging),
                    checked = s.chargingOnly,
                    onCheckedChange = actions.onCharging,
                )
            }
            FwSwitchRow(
                title = stringResource(R.string.backup_encrypt),
                subtitle = stringResource(R.string.backup_encrypt_body),
                checked = s.encrypted,
                onCheckedChange = { on -> if (on) settingPassphrase = true else confirmDisable = true },
            )
            if (s.isSignedIn) {
                FwInkButton(
                    stringResource(R.string.backup_now),
                    actions.onBackUpNow,
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
                FwSecondaryButton(
                    stringResource(R.string.backup_restore),
                    actions.onShowBackups,
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
            FwOverline(stringResource(R.string.backup_file_section))
            Text(stringResource(R.string.backup_file_body), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwSecondaryButton(
                    stringResource(R.string.backup_save_file),
                    actions.onSaveFile,
                    Modifier.weight(1f),
                    enabled = !busy,
                )
                FwSecondaryButton(
                    stringResource(R.string.backup_open_file),
                    actions.onOpenFile,
                    Modifier.weight(1f),
                    enabled = !busy,
                )
            }
            Note(stringResource(R.string.backup_hidden_folder))
            if (s.isSignedIn) FwTextButton(stringResource(R.string.backup_disconnect), actions.onDisconnect)
            Box(Modifier.height(16.dp))
        }
    }
    if (settingPassphrase) {
        PassphraseSheet(onDismiss = { settingPassphrase = false }) { passphrase ->
            actions.onEnableEncryption(passphrase)
            settingPassphrase = false
        }
    }
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            containerColor = FwTheme.colors.surface,
            title = { Text(stringResource(R.string.backup_disable_title), style = FwTheme.type.h3) },
            text = {
                Text(
                    stringResource(R.string.backup_disable_body),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    actions.onDisableEncryption()
                }) { Text(stringResource(R.string.backup_disable_confirm), color = FwTheme.colors.negative) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDisable = false
                }) { Text(stringResource(R.string.backup_cancel), color = FwTheme.colors.ink) }
            },
        )
    }
    state.backups?.let { BackupList(it, actions) }
    state.confirmRestore?.let { ConfirmRestore(actions) }
    state.askPassphrase?.let { AskPassphrase(state.wrongPassphrase, actions) }
}

@Composable
private fun StatusHero(state: BackupUiState) {
    val s = state.settings
    val busy = state.busy
    val lastSuccess = s.lastSuccessUtcMs
    val (dot, title) = when {
        busy != null -> FwTheme.colors.warning to stringResource(busyLabel(busy))

        s.lastError != null -> FwTheme.colors.negative to stringResource(R.string.backup_status_error)

        lastSuccess != null ->
            FwTheme.colors.positive to
                stringResource(R.string.backup_status_ok, ago(lastSuccess, state.nowUtcMs))

        else -> FwTheme.colors.ink3 to stringResource(R.string.backup_status_never)
    }
    FwCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.busy != null) {
                CircularProgressIndicator(Modifier.size(14.dp), color = FwTheme.colors.ink, strokeWidth = 2.dp)
            } else {
                Box(Modifier.size(12.dp).background(dot, CircleShape))
            }
            Text(title, style = FwTheme.type.h3, color = FwTheme.colors.ink)
        }
        Text(
            listOfNotNull(
                s.accountEmail,
                s.lastSizeBytes?.let { sizeText(it) },
                if (s.encrypted) stringResource(R.string.backup_encrypted_tag) else null,
            ).joinToString(" · ").ifEmpty { stringResource(R.string.backup_not_connected) },
            style = FwTheme.type.bodyS,
            color = FwTheme.colors.ink2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ErrorBanner(error: BackupError, actions: BackupActions) {
    FwCard(contentPadding = PaddingValues(16.dp)) {
        Text(stringResource(errorText(error)), style = FwTheme.type.body, color = FwTheme.colors.negative)
        when (error) {
            BackupError.SignIn -> FwTextButton(stringResource(R.string.backup_reconnect), actions.onConnect)

            BackupError.Offline, BackupError.Unknown -> FwTextButton(
                stringResource(R.string.backup_try_again),
                actions.onBackUpNow,
            )

            else -> Unit
        }
    }
}

@Composable
private fun Note(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(FwIcons.Info), null, tint = FwTheme.colors.ink2, modifier = Modifier.size(18.dp))
        Text(text, style = FwTheme.type.caption, color = FwTheme.colors.ink2)
    }
}

/** Passphrase setup with the loud warning (design.md 27; PRD F-14.5): typed twice, no recovery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseSheet(onDismiss: () -> Unit, onSave: (CharArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val tooShort = first.length < MIN_PASSPHRASE
    val mismatch = second.isNotEmpty() && second != first
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
            Text(stringResource(R.string.passphrase_title), style = FwTheme.type.h3, color = FwTheme.colors.ink)
            Text(
                stringResource(R.string.passphrase_warning),
                style = FwTheme.type.body,
                color = FwTheme.colors.negative,
            )
            PassphraseField(
                first,
                { first = it },
                stringResource(R.string.passphrase_first),
                if (first.isNotEmpty() &&
                    tooShort
                ) {
                    pluralStringResource(R.plurals.passphrase_too_short, MIN_PASSPHRASE, MIN_PASSPHRASE)
                } else {
                    null
                },
            )
            PassphraseField(
                second,
                {
                    second = it
                },
                stringResource(
                    R.string.passphrase_second,
                ),
                if (mismatch) stringResource(R.string.passphrase_mismatch) else null,
            )
            FwInkButton(
                stringResource(R.string.passphrase_save),
                { onSave(first.toCharArray()) },
                Modifier.fillMaxWidth(),
                enabled = !tooShort && second == first,
            )
            Box(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PassphraseField(value: String, onChange: (String) -> Unit, label: String, error: String?) {
    FwTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        error = error,
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupList(backups: List<RemoteBackup>, actions: BackupActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onCloseBackups,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(Modifier.padding(horizontal = FwTheme.spacing.gutter).navigationBarsPadding()) {
            Text(stringResource(R.string.backup_choose), style = FwTheme.type.h3, color = FwTheme.colors.ink)
            if (backups.isEmpty()) {
                Text(
                    stringResource(R.string.backup_none_found),
                    style = FwTheme.type.body,
                    color = FwTheme.colors.ink2,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            backups.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { actions.onPickBackup(b) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(dateTime(b.createdAtUtcMs), style = FwTheme.type.body, color = FwTheme.colors.ink)
                        Text(
                            listOfNotNull(
                                sizeText(b.sizeBytes),
                                if (b.isEncrypted) stringResource(R.string.backup_encrypted_tag) else null,
                            ).joinToString(" · "),
                            style = FwTheme.type.caption,
                            color = FwTheme.colors.ink2,
                        )
                    }
                    if (b.isEncrypted) {
                        Icon(
                            painterResource(FwIcons.Lock),
                            null,
                            tint = FwTheme.colors.ink2,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConfirmRestore(actions: BackupActions) {
    AlertDialog(
        onDismissRequest = actions.onCancelRestore,
        containerColor = FwTheme.colors.surface,
        title = { Text(stringResource(R.string.restore_title), style = FwTheme.type.h3) },
        text = { Text(stringResource(R.string.restore_body), style = FwTheme.type.bodyS, color = FwTheme.colors.ink2) },
        confirmButton = {
            TextButton(onClick = {
                actions.onConfirmRestore(null)
            }) { Text(stringResource(R.string.restore_confirm), color = FwTheme.colors.negative) }
        },
        dismissButton = {
            TextButton(onClick = actions.onCancelRestore) {
                Text(stringResource(R.string.backup_cancel), color = FwTheme.colors.ink)
            }
        },
    )
}

@Composable
private fun AskPassphrase(wrong: Boolean, actions: BackupActions) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = actions.onCancelRestore,
        containerColor = FwTheme.colors.surface,
        title = { Text(stringResource(R.string.restore_passphrase_title), style = FwTheme.type.h3) },
        text = {
            PassphraseField(
                value,
                {
                    value = it
                },
                stringResource(
                    R.string.passphrase_first,
                ),
                if (wrong) stringResource(R.string.restore_wrong_passphrase) else null,
            )
        },
        confirmButton = {
            TextButton(onClick = { actions.onConfirmRestore(value.toCharArray()) }, enabled = value.isNotEmpty()) {
                Text(stringResource(R.string.restore_confirm), color = FwTheme.colors.ink)
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCancelRestore) {
                Text(stringResource(R.string.backup_cancel), color = FwTheme.colors.ink)
            }
        },
    )
}

@Composable
private fun ago(thenUtcMs: Long, nowUtcMs: Long): String {
    val minutes = ((nowUtcMs - thenUtcMs) / MS_PER_MINUTE).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.ago_now)

        minutes < MINUTES_PER_HOUR -> pluralStringResource(R.plurals.ago_minutes, minutes.toInt(), minutes.toInt())

        minutes < MINUTES_PER_DAY -> (minutes / MINUTES_PER_HOUR).toInt().let {
            pluralStringResource(R.plurals.ago_hours, it, it)
        }

        else -> (minutes / MINUTES_PER_DAY).toInt().let { pluralStringResource(R.plurals.ago_days, it, it) }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes < KB -> "$bytes B"
    bytes < KB * KB -> "${IndianNumberFormat.decimal(bytes / KB.toDouble(), 0)} KB"
    else -> "${IndianNumberFormat.decimal(bytes / (KB * KB).toDouble(), 1)} MB"
}

private fun dateTime(utcMs: Long): String = DateTimeFormatter.ofPattern(
    "d MMM yyyy · HH:mm",
    Locale.ENGLISH,
).format(Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault()))

private fun frequencyLabel(f: BackupFrequency): Int = when (f) {
    BackupFrequency.AfterChanges -> R.string.frequency_after_changes
    BackupFrequency.Daily -> R.string.frequency_daily
    BackupFrequency.Weekly -> R.string.frequency_weekly
    BackupFrequency.Manual -> R.string.frequency_manual
}

private fun busyLabel(busy: BackupBusy): Int = when (busy) {
    BackupBusy.Connecting -> R.string.busy_connecting
    BackupBusy.BackingUp -> R.string.busy_backing_up
    BackupBusy.Listing -> R.string.busy_listing
    BackupBusy.Restoring -> R.string.busy_restoring
    BackupBusy.Encrypting -> R.string.busy_encrypting
    BackupBusy.Exporting -> R.string.busy_exporting
}

internal fun errorText(error: BackupError): Int = when (error) {
    BackupError.SignIn -> R.string.error_sign_in
    BackupError.Offline -> R.string.error_offline
    BackupError.StorageFull -> R.string.error_storage_full
    BackupError.KeyMissing -> R.string.error_key_missing
    BackupError.Unknown -> R.string.error_unknown
}

private const val MIN_PASSPHRASE = 8
private const val KB = 1024L
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 1_440L
