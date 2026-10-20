package app.fuelwise.core.domain.privacy

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AppLockPolicyTest {
    private val now = 1_760_000_000_000L

    @Test
    fun `off never locks`() {
        assertThat(AppLockPolicy.shouldLock(false, 0, null, now)).isFalse()
        assertThat(AppLockPolicy.shouldLock(false, 0, now - 3_600_000, now)).isFalse()
    }

    @Test
    fun `cold start always locks`() {
        assertThat(AppLockPolicy.shouldLock(true, 15, null, now)).isTrue()
    }

    @Test
    fun `locks only after the timeout in the background`() {
        assertThat(AppLockPolicy.shouldLock(true, 5, now - 4 * 60_000, now)).isFalse()
        assertThat(AppLockPolicy.shouldLock(true, 5, now - 5 * 60_000, now)).isTrue()
    }

    @Test
    fun `zero minutes locks on any return`() {
        assertThat(AppLockPolicy.shouldLock(true, 0, now - 1, now)).isTrue()
    }

    @Test
    fun `a clock moved backwards fails closed`() {
        assertThat(AppLockPolicy.shouldLock(true, 15, now + 60_000, now)).isTrue()
    }
}
