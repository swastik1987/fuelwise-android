package app.fuelwise.core.domain.importing

import app.fuelwise.core.model.EnergyType
import app.fuelwise.core.model.PaymentMethod
import app.fuelwise.core.model.SystemCategory

/**
 * The words an import understands, keyed by [normalize]d text. English aliases are built in; the app adds the
 * labels it shows in the current language (category names, payment names, column headers) so a template filled in
 * Hindi imports too. Custom category names map to their ids.
 */
class ImportVocabulary(
    categories: Map<String, String> = emptyMap(),
    payments: Map<String, PaymentMethod> = emptyMap(),
    energies: Map<String, EnergyType> = emptyMap(),
    yes: Set<String> = emptySet(),
    no: Set<String> = emptySet(),
    headers: Map<ImportField, Set<String>> = emptyMap(),
) {
    val categories: Map<String, String> = ENGLISH_CATEGORIES + categories.normalizedKeys()
    val payments: Map<String, PaymentMethod> = ENGLISH_PAYMENTS + payments.normalizedKeys()
    val energies: Map<String, EnergyType> = ENGLISH_ENERGIES + energies.normalizedKeys()
    val yes: Set<String> = ENGLISH_YES + yes.map(::normalize)
    val no: Set<String> = ENGLISH_NO + no.map(::normalize)

    /** Exact header names per field, the template's labels among them. */
    val headers: Map<ImportField, Set<String>> = ImportField.entries.associateWith { field ->
        ENGLISH_HEADERS[field].orEmpty() + headers[field].orEmpty().map(::normalize)
    }

    fun category(text: String): String? = categories[normalize(text)]

    fun payment(text: String): PaymentMethod? = payments[normalize(text)]

    fun energy(text: String): EnergyType? = energies[normalize(text)]

    companion object {
        /** Lowercase letters and digits only, keeping Devanagari vowel signs: "Toll (FASTag)" → "tollfastag". */
        fun normalize(text: String): String = buildString {
            text.lowercase().forEach { c ->
                val type = Character.getType(c)
                val mark =
                    type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
                if (c.isLetterOrDigit() || mark) append(c)
            }
        }

        private fun <T> Map<String, T>.normalizedKeys() = mapKeys { normalize(it.key) }.filterKeys { it.isNotEmpty() }

        private fun aliases(vararg words: String) = words.map(::normalize).toSet()

        private val ENGLISH_CATEGORIES: Map<String, String> = buildMap {
            fun add(key: SystemCategory, vararg words: String) = aliases(*words).forEach { put(it, key.id) }
            add(SystemCategory.Service, "service", "servicing", "maintenance", "oil change", "general service")
            add(SystemCategory.Repair, "repair", "repairs", "mechanic", "breakdown")
            add(SystemCategory.Tyres, "tyres", "tyre", "tires", "tire", "wheel alignment", "puncture")
            add(SystemCategory.Battery, "battery")
            add(SystemCategory.Insurance, "insurance")
            add(SystemCategory.Puc, "puc", "pollution", "emission test", "pollution check")
            add(SystemCategory.Parking, "parking")
            add(SystemCategory.TollFastag, "toll fastag", "toll (fastag)", "fastag", "toll")
            add(SystemCategory.TollCash, "toll cash", "toll (cash)", "cash toll")
            add(SystemCategory.Wash, "wash", "car wash", "washing", "cleaning", "detailing", "wash/detailing")
            add(SystemCategory.Accessories, "accessories", "accessory", "tuning")
            add(SystemCategory.Fine, "fine", "challan", "fine/challan", "traffic fine", "penalty", "ticket")
            add(SystemCategory.RoadTax, "road tax", "tax")
            add(SystemCategory.Registration, "registration", "rto", "registration/rto")
            add(SystemCategory.LoanEmi, "loan emi", "emi", "loan", "financing")
            add(SystemCategory.Purchase, "purchase", "vehicle purchase")
            add(SystemCategory.ExtendedWarranty, "extended warranty", "warranty")
            add(SystemCategory.Other, "other", "others", "misc", "miscellaneous")
        }

        private val ENGLISH_PAYMENTS: Map<String, PaymentMethod> = buildMap {
            fun add(method: PaymentMethod, vararg words: String) = aliases(*words).forEach { put(it, method) }
            add(PaymentMethod.Upi, "upi", "gpay", "google pay", "phonepe", "paytm", "bhim")
            add(PaymentMethod.Card, "card", "credit card", "debit card", "credit", "debit")
            add(PaymentMethod.Cash, "cash")
            add(PaymentMethod.FastagWallet, "fastag", "fastag wallet", "wallet")
            add(PaymentMethod.FleetCard, "fleet card", "fleet")
        }

        private val ENGLISH_ENERGIES: Map<String, EnergyType> = buildMap {
            fun add(type: EnergyType, vararg words: String) = aliases(*words).forEach { put(it, type) }
            add(EnergyType.PETROL, "petrol", "gasoline", "gas", "unleaded", "premium petrol", "power", "speed", "xp95")
            add(EnergyType.DIESEL, "diesel")
            add(EnergyType.CNG, "cng")
            add(EnergyType.LPG, "lpg", "autogas")
            add(EnergyType.ELECTRIC, "electric", "ev", "electricity", "charge", "charging")
            add(EnergyType.ETHANOL_FLEX, "ethanol", "ethanol flex", "flex", "e85", "e100")
        }

        private val ENGLISH_YES = aliases("yes", "y", "true", "1", "full", "haan", "ha")
        private val ENGLISH_NO = aliases("no", "n", "false", "0", "partial", "nahi", "na")

        private val ENGLISH_HEADERS: Map<ImportField, Set<String>> = mapOf(
            ImportField.Vehicle to aliases("vehicle", "car", "bike", "vehicle name", "registration", "reg no"),
            ImportField.Date to aliases("date", "data", "day", "date time", "datetime", "fill date"),
            ImportField.Time to aliases("time"),
            ImportField.Odometer to aliases("odometer", "odo", "odometer km", "odo km", "odometer reading", "reading"),
            ImportField.EnergyType to aliases("fuel", "fuel type", "energy", "fuel/energy"),
            ImportField.TankNumber to aliases("tank", "tank number", "tank no"),
            ImportField.Quantity to aliases(
                "quantity", "qty", "litres", "liters", "volume", "fuel litres", "fuel liters", "kg", "kwh", "units",
            ),
            ImportField.UnitPrice to aliases(
                "price per unit",
                "rate",
                "unit price",
                "price per litre",
                "price per liter",
                "volume price",
                "price/l",
            ),
            ImportField.Total to aliases("total", "total cost", "cost", "price", "paid", "amount", "total amount"),
            ImportField.FullTank to aliases("full", "full tank", "is full", "tank full"),
            ImportField.MissedPrevious to aliases("missed", "missed previous", "missed fill up", "missed fillup"),
            ImportField.Station to aliases("station", "pump", "petrol pump", "fuel station", "city", "location"),
            ImportField.Payment to aliases("payment", "payment method", "paid by", "payment mode", "mode"),
            ImportField.Notes to aliases("notes", "note", "comment", "comments", "remarks", "description", "title"),
            ImportField.Category to aliases("category", "type", "expense type", "cost type", "expense category"),
            ImportField.Amount to aliases("amount", "cost", "total", "price", "paid", "expense"),
            ImportField.TollPlaza to aliases("toll plaza", "plaza"),
        )
    }
}
