package app.fuelwise.core.domain.entry

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class EntryRangeTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 23)

    private fun ms(d: LocalDate) = d.atStartOfDay(ist).toInstant().toEpochMilli()

    @Test
    fun `presets are local calendar ranges`() {
        assertThat(entryRange(EntryRangePreset.All, today, ist)).isEqualTo(EntryRange(null, null))
        assertThat(entryRange(EntryRangePreset.ThisMonth, today, ist))
            .isEqualTo(EntryRange(ms(LocalDate.of(2026, 10, 1)), ms(LocalDate.of(2026, 11, 1))))
        assertThat(entryRange(EntryRangePreset.LastThreeMonths, today, ist))
            .isEqualTo(EntryRange(ms(LocalDate.of(2026, 8, 1)), ms(LocalDate.of(2026, 11, 1))))
        assertThat(entryRange(EntryRangePreset.ThisYear, today, ist))
            .isEqualTo(EntryRange(ms(LocalDate.of(2026, 1, 1)), ms(LocalDate.of(2027, 1, 1))))
    }

    @Test
    fun `a custom range includes both days`() {
        val r = entryRange(EntryRangePreset.Custom, today, ist, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12))
        assertThat(r).isEqualTo(EntryRange(ms(LocalDate.of(2026, 10, 5)), ms(LocalDate.of(2026, 10, 13))))
        assertThat(entryRange(EntryRangePreset.Custom, today, ist, null, null)).isEqualTo(EntryRange(null, null))
    }
}
