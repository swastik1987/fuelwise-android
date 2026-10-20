package app.fuelwise.core.domain.units

import app.fuelwise.core.model.QuantityUnit
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DisplayUnitsTest {
    @Test
    fun `india defaults show the engine figure unchanged`() {
        val figure = DisplayUnits.INDIA.efficiency(16.8, QuantityUnit.LITRE)!!
        assertThat(figure.value).isEqualTo(16.8)
        assertThat(figure.label).isEqualTo(EfficiencyLabel.KM_PER_L)
        assertThat(DisplayUnits.INDIA.efficiency(24.0, QuantityUnit.KILOGRAM)!!.label)
            .isEqualTo(EfficiencyLabel.KM_PER_KG)
        assertThat(DisplayUnits.INDIA.efficiency(7.1, QuantityUnit.KILOWATT_HOUR)!!.label)
            .isEqualTo(EfficiencyLabel.KM_PER_KWH)
    }

    @Test
    fun `litres per 100 km inverts the figure`() {
        // PRD F-03.7 vector 3: 14.286 km/L = 7.000 L/100 km.
        val units = DisplayUnits(efficiency = EfficiencyUnit.QUANTITY_PER_100_DISTANCE)
        val figure = units.efficiency(100.0 / 7.0, QuantityUnit.LITRE)!!
        assertThat(figure.value).isWithin(1e-9).of(7.0)
        assertThat(figure.label).isEqualTo(EfficiencyLabel.L_PER_100KM)
        assertThat(units.higherIsBetter).isFalse()
        assertThat(units.efficiency(6.25, QuantityUnit.KILOWATT_HOUR)!!.label).isEqualTo(EfficiencyLabel.KWH_PER_100KM)
    }

    @Test
    fun `mpg matches the existing per-100-km conversion for liquids`() {
        val us = DisplayUnits(efficiency = EfficiencyUnit.MPG_US).efficiency(100.0 / 7.0, QuantityUnit.LITRE)!!
        val uk = DisplayUnits(efficiency = EfficiencyUnit.MPG_UK).efficiency(100.0 / 7.0, QuantityUnit.LITRE)!!
        assertThat(us.value).isWithin(0.01).of(33.60)
        assertThat(us.label).isEqualTo(EfficiencyLabel.MPG_US)
        assertThat(uk.value).isWithin(0.01).of(40.35)
        assertThat(uk.label).isEqualTo(EfficiencyLabel.MPG_UK)
    }

    @Test
    fun `mpg falls back to miles per kg and per kWh`() {
        val units = DisplayUnits(efficiency = EfficiencyUnit.MPG_US)
        val cng = units.efficiency(KM_PER_MILE * 15, QuantityUnit.KILOGRAM)!!
        assertThat(cng.value).isWithin(1e-9).of(15.0)
        assertThat(cng.label).isEqualTo(EfficiencyLabel.MI_PER_KG)
        assertThat(units.efficiency(8.0, QuantityUnit.KILOWATT_HOUR)!!.label).isEqualTo(EfficiencyLabel.MI_PER_KWH)
    }

    @Test
    fun `non-positive figures have no display value`() {
        assertThat(DisplayUnits.INDIA.efficiency(0.0, QuantityUnit.LITRE)).isNull()
        assertThat(DisplayUnits.INDIA.efficiency(-3.0, QuantityUnit.LITRE)).isNull()
    }

    @Test
    fun `quantities convert only for litres`() {
        val units = DisplayUnits(volume = VolumeUnit.US_GALLON)
        assertThat(units.quantity(LITRES_PER_US_GALLON * 10, QuantityUnit.LITRE)).isWithin(1e-9).of(10.0)
        assertThat(units.quantity(4.2, QuantityUnit.KILOGRAM)).isEqualTo(4.2)
    }
}
