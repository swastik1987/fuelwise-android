package app.fuelwise.feature.dataimport

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import app.fuelwise.core.domain.importing.ImportField
import app.fuelwise.core.domain.importing.ImportTemplate
import app.fuelwise.core.domain.importing.ImportVocabulary
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.model.Vehicle
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.vehicle.VehicleLabels
import java.util.Locale

/**
 * The words an import reads and the template writes, from string resources. The vocabulary takes every language the
 * app ships, so a template filled in while the app was in Hindi still imports after switching to English.
 */
internal object ImportTexts {
    private val LANGUAGES = listOf("en", "hi")

    @StringRes
    fun fieldLabel(field: ImportField): Int = when (field) {
        ImportField.Vehicle -> R.string.field_vehicle
        ImportField.Date -> R.string.field_date
        ImportField.Time -> R.string.field_time
        ImportField.Odometer -> R.string.field_odometer
        ImportField.EnergyType -> R.string.field_energy
        ImportField.TankNumber -> R.string.field_tank
        ImportField.Quantity -> R.string.field_quantity
        ImportField.UnitPrice -> R.string.field_unit_price
        ImportField.Total -> R.string.field_total
        ImportField.FullTank -> R.string.field_full
        ImportField.MissedPrevious -> R.string.field_missed
        ImportField.Station -> R.string.field_station
        ImportField.Category -> R.string.field_category
        ImportField.Amount -> R.string.field_amount
        ImportField.TollPlaza -> R.string.field_toll_plaza
        ImportField.Payment -> R.string.field_payment
        ImportField.Notes -> R.string.field_notes
    }

    @StringRes
    private fun help(field: ImportField): Int = when (field) {
        ImportField.Vehicle -> R.string.help_vehicle
        ImportField.Date -> R.string.help_date
        ImportField.Time -> R.string.help_time
        ImportField.Odometer -> R.string.help_odometer
        ImportField.EnergyType, ImportField.TankNumber -> R.string.help_energy
        ImportField.Quantity -> R.string.help_quantity
        ImportField.UnitPrice -> R.string.help_unit_price
        ImportField.Total -> R.string.help_total
        ImportField.FullTank -> R.string.help_full
        ImportField.MissedPrevious -> R.string.help_missed
        ImportField.Station -> R.string.help_station
        ImportField.Category -> R.string.help_category
        ImportField.Amount -> R.string.help_amount
        ImportField.TollPlaza -> R.string.help_toll_plaza
        ImportField.Payment -> R.string.help_payment
        ImportField.Notes -> R.string.help_notes
    }

    private fun categoryName(resources: Resources, category: Category): String =
        category.customName ?: category.systemKey?.let { resources.getString(EntryLabels.category(it)) }.orEmpty()

    fun vocabulary(context: Context, categories: List<Category>): ImportVocabulary {
        val all = (LANGUAGES.map { localized(context, it) } + context.resources).distinct()
        return ImportVocabulary(
            categories = all.flatMap { r -> categories.map { categoryName(r, it) to it.id } }.toMap(),
            payments = all.flatMap { r ->
                PaymentMethod.entries.map { r.getString(EntryLabels.payment(it)) to it }
            }.toMap(),
            energies = all.flatMap { r ->
                EnergyType.entries.map { r.getString(VehicleLabels.energy(it)) to it }
            }.toMap(),
            yes = all.map { it.getString(R.string.template_yes) }.toSet(),
            no = all.map { it.getString(R.string.template_no) }.toSet(),
            headers = ImportField.entries.associateWith { f -> all.map { it.getString(fieldLabel(f)) }.toSet() },
        )
    }

    // The app turns off Play language splits (app/build.gradle.kts), so every shipped language is installed.
    @SuppressLint("AppBundleLocaleChanges")
    private fun localized(context: Context, language: String): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(config).resources
    }

    /** Template labels and dropdown lists in the app's current language. */
    fun template(
        resources: Resources,
        vehicles: List<Vehicle>,
        categories: List<Category>,
    ): Pair<ImportTemplate.Labels, ImportTemplate.Lists> {
        val yes = resources.getString(R.string.template_yes)
        val no = resources.getString(R.string.template_no)
        val examples = mapOf(
            ImportField.Vehicle to vehicles.firstOrNull()?.name.orEmpty(),
            ImportField.Date to "16-10-2026",
            ImportField.Time to "09:30",
            ImportField.Odometer to "42318",
            ImportField.EnergyType to
                resources.getString(
                    VehicleLabels.energy(vehicles.firstOrNull()?.primaryTank?.energyType ?: EnergyType.PETROL),
                ),
            ImportField.Quantity to "40.25",
            ImportField.UnitPrice to "88.95",
            ImportField.Total to "3580.24",
            ImportField.FullTank to yes,
            ImportField.MissedPrevious to no,
            ImportField.Station to resources.getString(R.string.template_example_station),
            ImportField.Payment to resources.getString(EntryLabels.payment(PaymentMethod.Upi)),
            ImportField.Notes to resources.getString(R.string.template_example_notes),
            ImportField.Category to resources.getString(EntryLabels.category(SystemCategory.Service)),
            ImportField.Amount to "4850",
            ImportField.TollPlaza to resources.getString(R.string.template_example_plaza),
        )
        val labels = ImportTemplate.Labels(
            fuelSheet = resources.getString(R.string.template_sheet_fuel),
            expenseSheet = resources.getString(R.string.template_sheet_expenses),
            howToSheet = resources.getString(R.string.template_sheet_howto),
            headers = ImportField.entries.associateWith { resources.getString(fieldLabel(it)) },
            help = ImportField.entries.associateWith { resources.getString(help(it)) },
            examples = examples,
            howToTitle = resources.getString(R.string.template_howto_title),
            howToIntro = listOf(
                R.string.template_intro_rows,
                R.string.template_intro_vehicle,
                R.string.template_intro_units,
                R.string.template_intro_triangle,
                R.string.template_intro_import,
            ).map(resources::getString),
            howToColumns = Triple(
                resources.getString(R.string.template_col_column),
                resources.getString(R.string.template_col_meaning),
                resources.getString(R.string.template_col_example),
            ),
        )
        val energies = vehicles.flatMap { v -> v.tanks.map { it.energyType } }.distinct().ifEmpty { EnergyType.entries }
        val lists = ImportTemplate.Lists(
            vehicles = vehicles.map { it.name },
            energies = energies.map { resources.getString(VehicleLabels.energy(it)) },
            categories = categories.filter { !it.isHidden }.map { categoryName(resources, it) },
            payments = PaymentMethod.entries.map { resources.getString(EntryLabels.payment(it)) },
            yesNo = listOf(yes, no),
        )
        return labels to lists
    }
}
