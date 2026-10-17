package app.fuelwise.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MoneyTest {
    @Test
    fun `adds and subtracts in paise`() {
        val fill = Money(333_461) // ₹3,334.61
        val toll = Money.ofRupees(165)
        assertThat((fill + toll).paise).isEqualTo(349_961)
        assertThat((fill - toll).paise).isEqualTo(316_961)
    }

    @Test
    fun `sums a list`() {
        assertThat(listOf(Money(100), Money(250), Money(-50)).sum()).isEqualTo(Money(300))
    }

    @Test
    fun `overflow throws instead of wrapping`() {
        assertThrows<ArithmeticException> { Money(Long.MAX_VALUE) + Money(1) }
    }
}
