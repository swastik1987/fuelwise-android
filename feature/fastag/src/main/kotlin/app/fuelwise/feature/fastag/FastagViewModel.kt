package app.fuelwise.feature.fastag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.EntryRepository
import app.fuelwise.core.data.repository.FastagRepository
import app.fuelwise.core.data.repository.VehicleRepository
import app.fuelwise.core.domain.recurrence.FastagBalance
import app.fuelwise.core.domain.recurrence.balance
import app.fuelwise.core.model.FastagAccount
import app.fuelwise.core.model.FastagRecharge
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.format.MoneyInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A toll or a recharge in the FASTag list, newest first. */
data class FastagItem(val id: String, val atUtcMs: Long, val amount: Money, val label: String?, val isRecharge: Boolean)

data class FastagCard(
    val vehicle: Vehicle,
    val account: FastagAccount?,
    val balance: FastagBalance?,
    val recent: List<FastagItem>,
)

/** What the account sheet typed, as text; parsed when saved. */
data class AccountInput(
    val vehicleId: String,
    val existing: FastagAccount?,
    val issuer: String,
    val last4: String,
    val balance: String,
    val threshold: String,
)

data class FastagUiState(val loading: Boolean = true, val cards: List<FastagCard> = emptyList())

/** FASTag (design.md screen 26; PRD F-10.1, F-10.3). */
@HiltViewModel
class FastagViewModel @Inject constructor(
    private val fastag: FastagRepository,
    vehicles: VehicleRepository,
    entries: EntryRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    val state: StateFlow<FastagUiState> = combine(
        vehicles.observeVehicles(),
        fastag.observeAccounts(),
        fastag.observeRecharges(),
        entries.observeAllExpenses(),
    ) { list, accounts, recharges, expenses ->
        FastagUiState(
            loading = false,
            cards = list.map { v ->
                val account = accounts.firstOrNull { it.vehicleId == v.id }
                val tolls = expenses.filter { it.vehicleId == v.id && it.categoryId == SystemCategory.TollFastag.id }
                val items =
                    tolls.map { FastagItem(it.id, it.dateTimeUtcMs, it.amount, it.tollPlaza, isRecharge = false) } +
                        recharges.filter {
                            it.vehicleId == v.id
                        }.map { FastagItem(it.id, it.dateTimeUtcMs, it.amount, it.notes, isRecharge = true) }
                FastagCard(
                    v,
                    account,
                    account?.balance(recharges, expenses),
                    items.sortedByDescending {
                        it.atUtcMs
                    }.take(RECENT),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FastagUiState())

    /** Sets up or edits the account; typing a balance moves the anchor to now (PRD F-10.3). */
    fun saveAccount(input: AccountInput) {
        val existing = input.existing
        viewModelScope.launch {
            val typedBalance = MoneyInput.parse(input.balance)
            val anchorMoved = typedBalance != null && typedBalance != existing?.anchorBalance
            fastag.saveAccount(
                FastagAccount(
                    id = existing?.id.orEmpty(),
                    vehicleId = input.vehicleId,
                    issuer = input.issuer,
                    // Never more than the last 4 digits (PRD F-10.1), whatever was pasted.
                    tagLast4 = input.last4.filter(Char::isDigit).takeLast(TAG_DIGITS).ifEmpty { null },
                    anchorBalance = typedBalance ?: existing?.anchorBalance ?: Money.ZERO,
                    anchorAtUtcMs = if (anchorMoved || existing == null) clock.millis() else existing.anchorAtUtcMs,
                    lowBalanceThreshold = MoneyInput.parse(input.threshold) ?: existing?.lowBalanceThreshold
                        ?: Money(FastagAccount.DEFAULT_LOW_BALANCE_PAISE),
                ),
            )
        }
    }

    /** A top-up: a transfer, not a vehicle expense, so it never counts in ₹/km. */
    fun addRecharge(account: FastagAccount, amount: String, note: String) {
        val money = MoneyInput.parse(amount)?.takeIf { it.paise > 0 } ?: return
        viewModelScope.launch {
            fastag.saveRecharge(
                FastagRecharge(
                    "",
                    account.id,
                    account.vehicleId,
                    clock.millis(),
                    zoneId.id,
                    money,
                    note.trim().ifEmpty {
                        null
                    },
                ),
            )
        }
    }

    fun deleteRecharge(id: String) {
        viewModelScope.launch { fastag.deleteRecharge(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECENT = 10
        const val TAG_DIGITS = 4
    }
}
