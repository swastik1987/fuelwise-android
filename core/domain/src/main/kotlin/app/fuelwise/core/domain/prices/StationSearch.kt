package app.fuelwise.core.domain.prices

import app.fuelwise.core.model.EnergyType
import java.net.URLEncoder

/**
 * "Find a petrol pump" (PRD F-09.4): a Google Maps search the user's Maps app runs itself. The app sends no
 * coordinates and calls no API; Maps resolves "near me" with its own location permission.
 */
object StationSearch {
    /** Search words per fuel. English on purpose: Maps matches place categories best in English. */
    fun query(energy: EnergyType?): String = when (energy) {
        EnergyType.CNG -> "CNG station near me"
        EnergyType.ELECTRIC -> "EV charging station near me"
        EnergyType.LPG -> "autogas LPG station near me"
        EnergyType.DIESEL -> "diesel pump near me"
        EnergyType.PETROL, EnergyType.ETHANOL_FLEX, null -> "petrol pump near me"
    }

    /**
     * The Maps URLs universal search link; opens the Maps app when installed, else the browser. Encoded with the
     * String charset overload: the Charset one needs API 33.
     */
    fun mapsUrl(energy: EnergyType?): String =
        "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query(energy), "UTF-8")
}
