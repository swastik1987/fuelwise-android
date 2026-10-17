package app.fuelwise.core.domain.id

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Test

class UuidV7GeneratorTest {
    private val fixedInstant = Instant.parse("2026-10-16T04:30:00Z")
    private val generator = UuidV7Generator(Clock.fixed(fixedInstant, ZoneOffset.UTC))

    @Test
    fun `is a valid version 7, variant 2 UUID`() {
        val uuid = UUID.fromString(generator.newId())
        assertThat(uuid.version()).isEqualTo(7)
        assertThat(uuid.variant()).isEqualTo(2)
    }

    @Test
    fun `embeds the clock time in the first 48 bits`() {
        val uuid = UUID.fromString(generator.newId())
        assertThat(uuid.mostSignificantBits ushr 16).isEqualTo(fixedInstant.toEpochMilli())
    }

    @Test
    fun `ids from the same millisecond sort in creation order`() {
        val ids = List(5_000) { generator.newId() }
        assertThat(ids).isInStrictOrder()
        assertThat(ids.toSet()).hasSize(ids.size)
    }

    @Test
    fun `canonical lowercase format`() {
        assertThat(generator.newId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
