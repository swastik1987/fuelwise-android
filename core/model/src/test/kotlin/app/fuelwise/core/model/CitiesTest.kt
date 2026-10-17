package app.fuelwise.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CitiesTest {
    @Test
    fun `popular chips follow the design order`() {
        assertThat(Cities.popular.map { it.name })
            .containsExactly("Bengaluru", "Mumbai", "Delhi", "Chennai", "Hyderabad", "Pune", "Kolkata", "Ahmedabad")
            .inOrder()
    }

    @Test
    fun `ids are unique`() {
        assertThat(Cities.all.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `search matches city or state, ignoring case`() {
        assertThat(Cities.search("myS").map { it.id }).containsExactly("mysuru")
        assertThat(Cities.search("kerala").map { it.id }).containsExactly("kochi", "thiruvananthapuram")
        assertThat(Cities.search("  ")).isEmpty()
    }
}
