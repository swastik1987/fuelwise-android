package app.fuelwise.core.domain.insights

import app.fuelwise.core.domain.engine.Period
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CustomRangeTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    private fun ms(d: LocalDate) = d.atStartOfDay(ist).toInstant().toEpochMilli()

    @Test
    fun `a picked range covers both days and compares with the same days before`() {
        // 5–12 Oct is 8 days; the 8 days before are 27 Sep – 4 Oct.
        val r = InsightPeriod.Custom.resolve(
            0,
            ist,
            null,
            CustomRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)),
        )
        assertThat(r.current).isEqualTo(Period(ms(LocalDate.of(2026, 10, 5)), ms(LocalDate.of(2026, 10, 13))))
        assertThat(r.previous).isEqualTo(Period(ms(LocalDate.of(2026, 9, 27)), ms(LocalDate.of(2026, 10, 5))))
    }

    @Test
    fun `one day works, and without a range Custom falls back to this month`() {
        val day = LocalDate.of(2026, 10, 5)
        assertThat(CustomRange(day, day).resolve(ist).current).isEqualTo(Period(ms(day), ms(day.plusDays(1))))
        val now = ms(LocalDate.of(2026, 10, 23))
        assertThat(InsightPeriod.Custom.resolve(now, ist, null)).isEqualTo(InsightPeriod.Month.resolve(now, ist, null))
        assertThrows<IllegalArgumentException> { CustomRange(day, day.minusDays(1)) }
    }
}
