package app.fuelwise.core.model

/**
 * Expense category (PRD F-04.2). System presets have a [systemKey] whose display name comes from string resources
 * (so it's translated); a user rename sets [customName]. System presets can be hidden or renamed but not deleted.
 */
data class Category(
    val id: String,
    val systemKey: SystemCategory?,
    val customName: String?,
    val isHidden: Boolean,
    val includeInCostPerKm: Boolean,
    val sortOrder: Int,
    /** A custom category's icon key (PRD F-04.2); system presets have their own. */
    val icon: String? = null,
) {
    val isSystem: Boolean get() = systemKey != null
}

/**
 * System presets, in display order. IDs are fixed so every device and every backup agrees on them;
 * they use the UUIDv7 layout with a zero timestamp. Purchase and Loan EMI are excluded from cost/km by default.
 */
enum class SystemCategory(val id: String, val includeInCostPerKm: Boolean = true) {
    Service("00000000-0000-7000-8000-000000000001"),
    Repair("00000000-0000-7000-8000-000000000002"),
    Tyres("00000000-0000-7000-8000-000000000003"),
    Battery("00000000-0000-7000-8000-000000000004"),
    Insurance("00000000-0000-7000-8000-000000000005"),
    Puc("00000000-0000-7000-8000-000000000006"),
    Parking("00000000-0000-7000-8000-000000000007"),
    TollFastag("00000000-0000-7000-8000-000000000008"),
    TollCash("00000000-0000-7000-8000-000000000009"),
    Wash("00000000-0000-7000-8000-00000000000a"),
    Accessories("00000000-0000-7000-8000-00000000000b"),
    Fine("00000000-0000-7000-8000-00000000000c"),
    RoadTax("00000000-0000-7000-8000-00000000000d"),
    Registration("00000000-0000-7000-8000-00000000000e"),
    LoanEmi("00000000-0000-7000-8000-00000000000f", includeInCostPerKm = false),
    Purchase("00000000-0000-7000-8000-000000000010", includeInCostPerKm = false),
    ExtendedWarranty("00000000-0000-7000-8000-000000000011"),
    Other("00000000-0000-7000-8000-000000000012"),
}
