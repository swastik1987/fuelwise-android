package app.fuelwise.core.database

import app.fuelwise.core.model.SystemCategory

/** Seed rows for [SystemCategory], in display order. */
internal object SystemCategorySeed {
    data class Row(val id: String, val systemKey: String, val includeInCostPerKm: Boolean, val sortOrder: Int)

    val rows: List<Row> = SystemCategory.entries.mapIndexed { index, category ->
        Row(category.id, category.name, category.includeInCostPerKm, index)
    }
}
