package app.fuelwise.core.domain.reminder

import app.fuelwise.core.model.ReminderKind
import app.fuelwise.core.model.SystemCategory
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ReminderCostTest {
    @Test
    fun remindersMapToTheCategoryTheirCostGoesUnder() {
        assertThat(ReminderKind.Insurance.costCategory()).isEqualTo(SystemCategory.Insurance)
        assertThat(ReminderKind.Puc.costCategory()).isEqualTo(SystemCategory.Puc)
        assertThat(ReminderKind.EngineOil.costCategory()).isEqualTo(SystemCategory.Service)
        assertThat(ReminderKind.Rc.costCategory()).isEqualTo(SystemCategory.Registration)
        assertThat(ReminderKind.Custom.costCategory()).isEqualTo(SystemCategory.Other)
        // A top-up is not a cost; the tolls paid from it are.
        assertThat(ReminderKind.FastagLowBalance.costCategory()).isNull()
    }

    @Test
    fun roundTripsThroughANotificationExtra() {
        val cost = ReminderCost("c-1", "safari", SystemCategory.Insurance.id)
        assertThat(ReminderCost.decode(cost.encode())).isEqualTo(cost)
        assertThat(ReminderCost.decode(null)).isNull()
        assertThat(ReminderCost.decode("2\tc-1\tsafari\tx")).isNull()
        assertThat(ReminderCost.decode("1\t\tsafari\tx")).isNull()
    }
}
