package app.fuelwise.core.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UnitsTest {
    @Test
    fun `km per litre from litres per 100 km`() {
        // PRD F-03.7 vector 3: 35 L over 500 km = 7.000 L/100km = 14.286 km/L
        val kmPerL = EfficiencyUnit.DISTANCE_PER_QUANTITY.fromPer100Km(7.0)!!
        assertThat(kmPerL).isWithin(0.001).of(14.286)
    }

    @Test
    fun `quantity per 100 km passes through`() {
        assertThat(EfficiencyUnit.QUANTITY_PER_100_DISTANCE.fromPer100Km(6.667)).isEqualTo(6.667)
    }

    @Test
    fun `mpg conversions`() {
        assertThat(EfficiencyUnit.MPG_US.fromPer100Km(7.0)!!).isWithin(0.01).of(33.60)
        assertThat(EfficiencyUnit.MPG_UK.fromPer100Km(7.0)!!).isWithin(0.01).of(40.35)
    }

    @Test
    fun `non-positive consumption has no efficiency`() {
        assertThat(EfficiencyUnit.DISTANCE_PER_QUANTITY.fromPer100Km(0.0)).isNull()
    }

    @Test
    fun `distance and volume round-trip`() {
        assertThat(DistanceUnit.MILE.fromKm(DistanceUnit.MILE.toKm(26.2))).isWithin(1e-9).of(26.2)
        assertThat(VolumeUnit.US_GALLON.toLitres(1.0)).isWithin(1e-9).of(3.785411784)
        assertThat(DistanceUnit.KILOMETRE.fromKm(42_318.0)).isEqualTo(42_318.0)
    }
}
