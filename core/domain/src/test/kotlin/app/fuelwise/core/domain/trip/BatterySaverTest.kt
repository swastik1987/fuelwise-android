package app.fuelwise.core.domain.trip

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BatterySaverTest {
    private val day = 24L * 60 * 60 * 1_000

    @Test
    fun `phone families`() {
        assertThat(phoneBrand("Xiaomi", "Redmi")).isEqualTo(PhoneBrand.Xiaomi)
        assertThat(phoneBrand("Xiaomi", "POCO")).isEqualTo(PhoneBrand.Xiaomi)
        assertThat(phoneBrand("samsung")).isEqualTo(PhoneBrand.Samsung)
        assertThat(phoneBrand("realme")).isEqualTo(PhoneBrand.Oppo)
        assertThat(phoneBrand("OnePlus")).isEqualTo(PhoneBrand.Oppo)
        assertThat(phoneBrand("vivo", "iQOO")).isEqualTo(PhoneBrand.Vivo)
        assertThat(phoneBrand("HONOR")).isEqualTo(PhoneBrand.Huawei)
        assertThat(phoneBrand("Google", "google")).isEqualTo(PhoneBrand.Other)
    }

    @Test
    fun `blocked only when silent for 3 days and a fill-up shows driving`() {
        val now = 100 * day
        // Silent 4 days, filled up yesterday: blocked.
        assertThat(autoTripsLikelyBlocked(null, now - 4 * day, listOf(now - day), now)).isTrue()
        // Silent 4 days, no fill-up since: maybe just not driving.
        assertThat(autoTripsLikelyBlocked(null, now - 4 * day, listOf(now - 5 * day), now)).isFalse()
        // Silent only 2 days.
        assertThat(autoTripsLikelyBlocked(null, now - 2 * day, listOf(now - day), now)).isFalse()
        // Never an event since turning on 5 days ago, and a fill-up since: blocked.
        assertThat(autoTripsLikelyBlocked(now - 5 * day, null, listOf(now - day), now)).isTrue()
        // Not on at all.
        assertThat(autoTripsLikelyBlocked(null, null, listOf(now - day), now)).isFalse()
    }
}
