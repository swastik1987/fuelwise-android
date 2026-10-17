package app.fuelwise.core.domain.vehicle

/**
 * Indian registration numbers (PRD F-01.1). Accepts the state format (KA 01 AB 1234, DL 3C EF 9012) and the
 * Bharat series (22 BH 1234 AA). Input may use any spacing, hyphens or case.
 */
object RegistrationNumber {
    private val STATE = Regex("^([A-Z]{2})(\\d{1,2})([A-Z]{0,3})(\\d{1,4})$")
    private val BHARAT = Regex("^(\\d{2})(BH)(\\d{4})([A-Z]{1,2})$")
    private val SEPARATORS = Regex("[\\s-]+")

    /** Uppercase with spaces and hyphens removed: "ka-01 ab 1234" → "KA01AB1234". Use for comparisons. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    fun isValid(input: String): Boolean {
        val n = normalize(input)
        return STATE.matches(n) || BHARAT.matches(n)
    }

    /**
     * Display form, or null if invalid. Keeps the user's own grouping when they typed separators, because some
     * RTO codes include a letter (Delhi's "3C") that can't be told apart from the series. Unspaced input gets the
     * default grouping: "KA01AB1234" → "KA 01 AB 1234", "22BH1234AA" → "22 BH 1234 AA".
     */
    fun format(input: String): String? {
        if (!isValid(input)) return null
        val typedGroups = input.trim().uppercase().split(SEPARATORS).filter { it.isNotEmpty() }
        if (typedGroups.size > 1) return typedGroups.joinToString(" ")
        val n = normalize(input)
        val match = STATE.matchEntire(n) ?: BHARAT.matchEntire(n)
        return match?.groupValues?.drop(1)?.filter { it.isNotEmpty() }?.joinToString(" ")
    }
}
