package app.fuelwise.core.model

/** A city for fuel prices (PRD F-09.1). The price feed will key its rows by [id]. */
data class City(val id: String, val name: String, val state: String)

/**
 * Cities offered for the home-city choice until the price feed (D2/D3) supplies the full list. Metros first, in
 * the order design.md screen 3 shows as chips, then state capitals and large cities alphabetically.
 */
object Cities {
    val popular: List<City> = listOf(
        City("bengaluru", "Bengaluru", "Karnataka"),
        City("mumbai", "Mumbai", "Maharashtra"),
        City("delhi", "Delhi", "Delhi"),
        City("chennai", "Chennai", "Tamil Nadu"),
        City("hyderabad", "Hyderabad", "Telangana"),
        City("pune", "Pune", "Maharashtra"),
        City("kolkata", "Kolkata", "West Bengal"),
        City("ahmedabad", "Ahmedabad", "Gujarat"),
    )

    val others: List<City> = listOf(
        City("bhopal", "Bhopal", "Madhya Pradesh"),
        City("bhubaneswar", "Bhubaneswar", "Odisha"),
        City("chandigarh", "Chandigarh", "Chandigarh"),
        City("coimbatore", "Coimbatore", "Tamil Nadu"),
        City("dehradun", "Dehradun", "Uttarakhand"),
        City("ghaziabad", "Ghaziabad", "Uttar Pradesh"),
        City("gurugram", "Gurugram", "Haryana"),
        City("guwahati", "Guwahati", "Assam"),
        City("indore", "Indore", "Madhya Pradesh"),
        City("jaipur", "Jaipur", "Rajasthan"),
        City("kochi", "Kochi", "Kerala"),
        City("lucknow", "Lucknow", "Uttar Pradesh"),
        City("mysuru", "Mysuru", "Karnataka"),
        City("nagpur", "Nagpur", "Maharashtra"),
        City("noida", "Noida", "Uttar Pradesh"),
        City("panaji", "Panaji", "Goa"),
        City("patna", "Patna", "Bihar"),
        City("raipur", "Raipur", "Chhattisgarh"),
        City("ranchi", "Ranchi", "Jharkhand"),
        City("surat", "Surat", "Gujarat"),
        City("thiruvananthapuram", "Thiruvananthapuram", "Kerala"),
        City("vadodara", "Vadodara", "Gujarat"),
        City("visakhapatnam", "Visakhapatnam", "Andhra Pradesh"),
    )

    val all: List<City> = popular + others

    fun byId(id: String?): City? = all.firstOrNull { it.id == id }

    /** Case-insensitive match on city or state name; a blank query returns nothing (chips cover that case). */
    fun search(query: String): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return all.filter { it.name.contains(q, ignoreCase = true) || it.state.contains(q, ignoreCase = true) }
    }
}
