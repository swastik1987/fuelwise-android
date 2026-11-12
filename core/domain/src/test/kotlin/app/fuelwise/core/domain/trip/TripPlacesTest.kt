package app.fuelwise.core.domain.trip

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TripPlacesTest {
    // Home in HSR Layout; 0.001° of latitude ≈ 111 m.
    private val home = PrivatePlace("home", "Home", 12.9100, 77.6400)
    private val office = PrivatePlace("office", "Office", 12.9780, 77.7500)

    @Test
    fun `the neighbourhood wins, then the city, then the district`() {
        val full = AddressParts("Indiranagar", "Bengaluru", "Bangalore Urban", "100 Feet Road")
        assertThat(placeLabel(full)).isEqualTo("Indiranagar")
        assertThat(placeLabel(full.copy(subLocality = " "))).isEqualTo("Bengaluru")
        assertThat(placeLabel(AddressParts(subAdminArea = "Gurugram"))).isEqualTo("Gurugram")
    }

    @Test
    fun `a feature name counts only when it is a real name`() {
        assertThat(placeLabel(AddressParts(featureName = "Lonavala Ghat"))).isEqualTo("Lonavala Ghat")
        // House numbers and plus codes aren't places.
        assertThat(placeLabel(AddressParts(featureName = "42"))).isNull()
        assertThat(placeLabel(AddressParts(featureName = "7J4V+2X"))).isNull()
        assertThat(placeLabel(AddressParts())).isNull()
    }

    @Test
    fun `a stored end just outside a private place is still named after it`() {
        // 333 m from home: outside the 200 m trim, inside the 400 m naming radius.
        assertThat(privatePlaceFor(12.9130, 77.6400, listOf(home, office))?.name).isEqualTo("Home")
        // 555 m: looked up instead.
        assertThat(privatePlaceFor(12.9150, 77.6400, listOf(home, office))).isNull()
        assertThat(privatePlaceFor(12.9100, 77.6400, emptyList())).isNull()
    }

    @Test
    fun `lookups use about 110 m precision`() {
        assertThat(roundForLookup(12.971598)).isEqualTo(12.972)
        assertThat(roundForLookup(77.594566)).isEqualTo(77.595)
    }
}
