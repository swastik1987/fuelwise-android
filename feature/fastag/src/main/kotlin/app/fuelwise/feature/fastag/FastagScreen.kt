package app.fuelwise.feature.fastag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwCard
import app.fuelwise.core.designsystem.component.FwEmptyState
import app.fuelwise.core.designsystem.component.FwInfoSheet
import app.fuelwise.core.designsystem.component.FwInkButton
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwSecondaryButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.Money
import app.fuelwise.core.ui.format.IndianNumberFormat
import app.fuelwise.core.ui.format.MoneyInput
import java.time.Instant
import java.time.ZoneId

internal data class FastagActions(
    val onBack: () -> Unit = {},
    val onAddToll: (String) -> Unit = {},
    val onSaveAccount: (AccountInput) -> Unit = {},
    val onAddRecharge: (FastagAccount, String, String) -> Unit = { _, _, _ -> },
)

@Composable
internal fun FastagScreen(
    onBack: () -> Unit,
    onAddToll: (String) -> Unit,
    viewModel: FastagViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FastagContent(state, FastagActions(onBack, onAddToll, viewModel::saveAccount, viewModel::addRecharge))
}

/** FASTag (design.md screen 26): per vehicle, the estimated balance and recent tolls and recharges. */
@Composable
internal fun FastagContent(state: FastagUiState, actions: FastagActions) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var recharging by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.fastag_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.fastag_back),
        )
        if (state.loading) return@Column
        if (state.cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FwEmptyState(
                    icon = FwIcons.Toll,
                    headline = stringResource(R.string.fastag_no_vehicles),
                    body = stringResource(R.string.fastag_no_vehicles_body),
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = FwTheme.spacing.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.cards, key = { it.vehicle.id }) { card ->
                AccountCard(card, onEdit = {
                    editing = card.vehicle.id
                }, onRecharge = { recharging = card.vehicle.id }, actions.onAddToll, {
                    info =
                        true
                })
            }
        }
    }
    editing?.let { vehicleId ->
        val card = state.cards.firstOrNull { it.vehicle.id == vehicleId }
        AccountSheet(card?.account, onDismiss = { editing = null }) { issuer, last4, balance, threshold ->
            actions.onSaveAccount(AccountInput(vehicleId, card?.account, issuer, last4, balance, threshold))
            editing = null
        }
    }
    recharging?.let { vehicleId ->
        state.cards.firstOrNull { it.vehicle.id == vehicleId }?.account?.let { account ->
            RechargeSheet(onDismiss = { recharging = null }) { amount, note ->
                actions.onAddRecharge(account, amount, note)
                recharging = null
            }
        }
    }
    if (info) {
        FwInfoSheet(
            stringResource(
                R.string.fastag_info_title,
            ),
            listOf(stringResource(R.string.fastag_info_body)),
            onDismiss = {
                info =
                    false
            },
        )
    }
}

@Composable
private fun AccountCard(
    card: FastagCard,
    onEdit: () -> Unit,
    onRecharge: () -> Unit,
    onAddToll: (String) -> Unit,
    onInfo: () -> Unit,
) {
    FwCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(card.vehicle.name, style = FwTheme.type.label, color = FwTheme.colors.ink)
                card.account?.let { a ->
                    Text(
                        listOfNotNull(a.issuer, a.tagLast4?.let { "••••$it" }).joinToString(" · "),
                        style = FwTheme.type.caption,
                        color = FwTheme.colors.ink2,
                    )
                }
            }
            if (card.account != null) {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(FwIcons.Edit), stringResource(R.string.fastag_edit), tint = FwTheme.colors.ink)
                }
            }
        }
        val account = card.account
        val balance = card.balance
        if (account == null || balance == null) {
            Text(
                stringResource(R.string.fastag_setup_body),
                style = FwTheme.type.bodyS,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            FwSecondaryButton(stringResource(R.string.fastag_setup), onEdit, Modifier.fillMaxWidth())
            return@FwCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text(
                IndianNumberFormat.rupeesWhole(balance.estimate),
                style = FwTheme.type.display,
                color = if (balance.isLow) FwTheme.colors.negative else FwTheme.colors.ink,
            )
            Text(
                stringResource(R.string.fastag_est),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
                modifier = Modifier.padding(
                    start = 8.dp,
                ).background(FwTheme.colors.surface2, FwTheme.radius.pill).padding(horizontal = 8.dp, vertical = 2.dp),
            )
            IconButton(onClick = onInfo) {
                Icon(
                    painterResource(FwIcons.Info),
                    stringResource(R.string.fastag_info_title),
                    tint = FwTheme.colors.ink2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            stringResource(
                if (balance.isLow) R.string.fastag_low else R.string.fastag_threshold,
                IndianNumberFormat.rupeesWhole(account.lowBalanceThreshold),
            ),
            style = FwTheme.type.caption,
            color = if (balance.isLow) FwTheme.colors.negative else FwTheme.colors.ink2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
            FwSecondaryButton(stringResource(R.string.fastag_add_toll), {
                onAddToll(card.vehicle.id)
            }, Modifier.weight(1f), leadingIcon = FwIcons.Toll)
            FwSecondaryButton(
                stringResource(R.string.fastag_add_recharge),
                onRecharge,
                Modifier.weight(1f),
                leadingIcon = FwIcons.Add,
            )
        }
        if (card.recent.isNotEmpty()) {
            FwOverline(stringResource(R.string.fastag_recent), Modifier.padding(top = 16.dp, bottom = 4.dp))
            card.recent.forEach { item ->
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.label
                                ?: stringResource(
                                    if (item.isRecharge) R.string.fastag_recharge else R.string.fastag_toll,
                                ),
                            style = FwTheme.type.body,
                            color = FwTheme.colors.ink,
                        )
                        Text(
                            IndianNumberFormat.date(
                                Instant.ofEpochMilli(item.atUtcMs).atZone(ZoneId.systemDefault()).toLocalDate(),
                            ),
                            style = FwTheme.type.caption,
                            color = FwTheme.colors.ink2,
                        )
                    }
                    Text(
                        (if (item.isRecharge) "+" else "−") + IndianNumberFormat.rupeesWhole(item.amount),
                        style = FwTheme.type.label,
                        color = if (item.isRecharge) FwTheme.colors.positive else FwTheme.colors.ink,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    account: FastagAccount?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var issuer by rememberSaveable { mutableStateOf(account?.issuer.orEmpty()) }
    var last4 by rememberSaveable { mutableStateOf(account?.tagLast4.orEmpty()) }
    var balance by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable {
        mutableStateOf(MoneyInput.plain(account?.lowBalanceThreshold ?: Money(FastagAccount.DEFAULT_LOW_BALANCE_PAISE)))
    }
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
            Text(stringResource(R.string.fastag_account_title), style = FwTheme.type.h3, color = FwTheme.colors.ink)
            FwTextField(value = issuer, onValueChange = {
                issuer = it.take(MAX_ISSUER)
            }, label = stringResource(R.string.fastag_issuer))
            FwTextField(
                value = last4,
                onValueChange = { v -> last4 = v.filter(Char::isDigit).takeLast(TAG_DIGITS) },
                label = stringResource(R.string.fastag_last4),
                keyboardType = KeyboardType.Number,
            )
            FwTextField(
                value = balance,
                onValueChange = { v -> balance = v.filter { it.isDigit() || it == '.' }.take(MAX_AMOUNT) },
                label = stringResource(
                    if (account ==
                        null
                    ) {
                        R.string.fastag_balance_now
                    } else {
                        R.string.fastag_balance_correct
                    },
                ),
                unit = "₹",
                keyboardType = KeyboardType.Decimal,
            )
            FwTextField(
                value = threshold,
                onValueChange = { v -> threshold = v.filter { it.isDigit() || it == '.' }.take(MAX_AMOUNT) },
                label = stringResource(R.string.fastag_threshold_label),
                unit = "₹",
                keyboardType = KeyboardType.Decimal,
            )
            Text(stringResource(R.string.fastag_last4_note), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
            FwInkButton(stringResource(R.string.fastag_save), {
                onSave(issuer, last4, balance, threshold)
            }, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RechargeSheet(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
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
            Text(stringResource(R.string.fastag_add_recharge), style = FwTheme.type.h3, color = FwTheme.colors.ink)
            FwTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }.take(MAX_AMOUNT) },
                label = stringResource(R.string.fastag_amount),
                unit = "₹",
                keyboardType = KeyboardType.Decimal,
            )
            FwTextField(value = note, onValueChange = {
                note = it.take(MAX_ISSUER)
            }, label = stringResource(R.string.fastag_note))
            Text(
                stringResource(R.string.fastag_recharge_note),
                style = FwTheme.type.caption,
                color = FwTheme.colors.ink2,
            )
            FwInkButton(stringResource(R.string.fastag_save), { onSave(amount, note) }, Modifier.fillMaxWidth())
        }
    }
}

private const val TAG_DIGITS = 4
private const val MAX_ISSUER = 40
private const val MAX_AMOUNT = 10
