package app.fuelwise.core.ui.entry

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.EnergyColors
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.ChargerType
import app.fuelwise.core.model.FuelGrade
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Names, icons and tints for entry concepts, shared by the entry sheets, Logbook and Insights. */
object EntryLabels {
    @StringRes
    fun category(key: SystemCategory): Int = when (key) {
        SystemCategory.Service -> R.string.category_service
        SystemCategory.Repair -> R.string.category_repair
        SystemCategory.Tyres -> R.string.category_tyres
        SystemCategory.Battery -> R.string.category_battery
        SystemCategory.Insurance -> R.string.category_insurance
        SystemCategory.Puc -> R.string.category_puc
        SystemCategory.Parking -> R.string.category_parking
        SystemCategory.TollFastag -> R.string.category_toll_fastag
        SystemCategory.TollCash -> R.string.category_toll_cash
        SystemCategory.Wash -> R.string.category_wash
        SystemCategory.Accessories -> R.string.category_accessories
        SystemCategory.Fine -> R.string.category_fine
        SystemCategory.RoadTax -> R.string.category_road_tax
        SystemCategory.Registration -> R.string.category_registration
        SystemCategory.LoanEmi -> R.string.category_loan_emi
        SystemCategory.Purchase -> R.string.category_purchase
        SystemCategory.ExtendedWarranty -> R.string.category_warranty
        SystemCategory.Other -> R.string.category_other
    }

    @DrawableRes
    fun categoryIcon(key: SystemCategory?): Int = when (key) {
        SystemCategory.Service -> FwIcons.Service
        SystemCategory.Repair -> FwIcons.Repair
        SystemCategory.Tyres -> FwIcons.Tyres
        SystemCategory.Battery -> FwIcons.BatteryFull
        SystemCategory.Insurance -> FwIcons.Shield
        SystemCategory.Puc -> FwIcons.Eco
        SystemCategory.Parking -> FwIcons.Parking
        SystemCategory.TollFastag -> FwIcons.Toll
        SystemCategory.TollCash -> FwIcons.Cash
        SystemCategory.Wash -> FwIcons.CarWash
        SystemCategory.Accessories -> FwIcons.ShoppingBag
        SystemCategory.Fine -> FwIcons.Gavel
        SystemCategory.RoadTax -> FwIcons.Bank
        SystemCategory.Registration -> FwIcons.Document
        SystemCategory.LoanEmi -> FwIcons.CreditCard
        SystemCategory.Purchase -> FwIcons.Sell
        SystemCategory.ExtendedWarranty -> FwIcons.Verified
        SystemCategory.Other, null -> FwIcons.MoreHoriz
    }

    /** A category's icon: a custom category's chosen one (PRD F-04.2), else its preset's. */
    @DrawableRes
    fun categoryIcon(category: Category?): Int =
        category?.icon?.let { key -> CUSTOM_ICONS.firstOrNull { it.first == key }?.second } ?: categoryIcon(category?.systemKey)

    /** Icons a custom category can use, by stable key (stored on the row; never reorder keys). */
    val CUSTOM_ICONS: List<Pair<String, Int>> = listOf(
        "service" to FwIcons.Service,
        "repair" to FwIcons.Repair,
        "tyres" to FwIcons.Tyres,
        "parking" to FwIcons.Parking,
        "wash" to FwIcons.CarWash,
        "shopping" to FwIcons.ShoppingBag,
        "document" to FwIcons.Document,
        "card" to FwIcons.CreditCard,
        "cash" to FwIcons.Cash,
        "shield" to FwIcons.Shield,
        "route" to FwIcons.Route,
        "car" to FwIcons.Car,
        "bolt" to FwIcons.Bolt,
        "business" to FwIcons.Business,
        "person" to FwIcons.Person,
        "other" to FwIcons.MoreHoriz,
    )

    /** Tolls use the premium/other energy colour (design.md mocks); everything else is neutral ink-2. */
    fun categoryTint(key: SystemCategory?, neutral: Color): Color = when (key) {
        SystemCategory.TollFastag, SystemCategory.TollCash -> EnergyColors.Premium
        else -> neutral
    }

    @StringRes
    fun payment(method: PaymentMethod): Int = when (method) {
        PaymentMethod.Upi -> R.string.payment_upi
        PaymentMethod.Card -> R.string.payment_card
        PaymentMethod.Cash -> R.string.payment_cash
        PaymentMethod.FastagWallet -> R.string.payment_fastag
        PaymentMethod.FleetCard -> R.string.payment_fleet
    }

    @StringRes
    fun grade(grade: FuelGrade): Int = when (grade) {
        FuelGrade.Regular -> R.string.grade_regular
        FuelGrade.E20 -> R.string.grade_e20
        FuelGrade.Premium -> R.string.grade_premium
    }

    @StringRes
    fun charger(type: ChargerType): Int = when (type) {
        ChargerType.HomeAc -> R.string.charger_home
        ChargerType.PublicAc -> R.string.charger_public
        ChargerType.DcFast -> R.string.charger_dc
    }
}

/** A category's display name: the user's rename, else the translated system name. */
@Composable
fun categoryName(category: Category): String =
    category.customName ?: category.systemKey?.let { stringResource(EntryLabels.category(it)) }.orEmpty()

/** "Now · 16 Oct" for the current minute, "Today · 08:46", or "14 Oct · 18:40" (design.md screen 6). */
@Composable
fun entryDateTimeLabel(atUtcMs: Long, nowUtcMs: Long, zone: ZoneId, locale: Locale = Locale.ENGLISH): String {
    val at = Instant.ofEpochMilli(atUtcMs).atZone(zone)
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale).format(at)
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(at)
    val today = Instant.ofEpochMilli(nowUtcMs).atZone(zone).toLocalDate()
    return when {
        kotlin.math.abs(nowUtcMs - atUtcMs) < NOW_WINDOW_MS -> stringResource(R.string.entry_now, dayMonth)
        at.toLocalDate() == today -> stringResource(R.string.entry_today, time)
        else -> "$dayMonth · $time"
    }
}

private const val NOW_WINDOW_MS = 60_000L
