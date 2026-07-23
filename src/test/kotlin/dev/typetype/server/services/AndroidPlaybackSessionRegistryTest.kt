package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AndroidPlaybackSessionRegistryTest {
    @Test
    fun `registered Android session resolves and touches the isolated store`() {
        val current = MutableNow(Instant.parse("2026-07-20T10:00:00Z"))
        val store = mockk<SabrSessionStore>()
        val holder = holder()
        val subtitle = mockk<AndroidSubtitleTrack>()
        every { store.lookupByToken("android-session") } returns holder
        val registry = registry(store, current)
        registry.register(holder, listOf(subtitle))

        val result = registry.lookup("android-session") as AndroidPlaybackSessionLookup.Active

        assertSame(holder, result.session.holder)
        assertSame(subtitle, result.session.subtitles.single())
        verify(exactly = 1) { store.lookupByToken("android-session") }
    }

    @Test
    fun `idle session returns gone while unknown session returns not found`() {
        val current = MutableNow(Instant.parse("2026-07-20T10:00:00Z"))
        val store = mockk<SabrSessionStore>(relaxed = true)
        val holder = holder()
        val registry = registry(store, current)
        registry.register(holder, emptyList())
        current.value = current.value.plus(Duration.ofMinutes(5))

        assertEquals(AndroidPlaybackSessionLookup.Expired, registry.lookup("android-session"))
        assertEquals(AndroidPlaybackSessionLookup.Unknown, registry.lookup("never-created"))
        verify(exactly = 1) { store.release(holder) }
    }

    @Test
    fun `expired session tombstone is temporary`() {
        val current = MutableNow(Instant.parse("2026-07-20T10:00:00Z"))
        val store = mockk<SabrSessionStore>(relaxed = true)
        val registry = registry(store, current)
        registry.register(holder(), emptyList())
        current.value = current.value.plus(Duration.ofMinutes(5))
        assertEquals(AndroidPlaybackSessionLookup.Expired, registry.lookup("android-session"))

        current.value = current.value.plus(Duration.ofMinutes(11))

        assertEquals(AndroidPlaybackSessionLookup.Unknown, registry.lookup("android-session"))
    }

    @Test
    fun `missing backing Android session becomes expired`() {
        val current = MutableNow(Instant.parse("2026-07-20T10:00:00Z"))
        val store = mockk<SabrSessionStore>(relaxed = true)
        val holder = holder()
        every { store.lookupByToken("android-session") } returns null
        val registry = registry(store, current)
        registry.register(holder, emptyList())

        assertEquals(AndroidPlaybackSessionLookup.Expired, registry.lookup("android-session"))
        verify(exactly = 1) { store.release(holder) }
    }

    private fun registry(store: SabrSessionStore, current: MutableNow) = AndroidPlaybackSessionRegistry(
        store = store,
        idleTimeout = Duration.ofMinutes(4),
        tombstoneTtl = Duration.ofMinutes(10),
        now = current::get,
    )

    private fun holder(): SabrSessionHolder = mockk {
        every { sessionToken } returns "android-session"
        every { key } returns SabrSessionKey(
            "video",
            "user",
            140,
            null,
            137,
            0L,
            SabrSessionPurpose.ANDROID_PLAYBACK,
        )
    }

    private class MutableNow(var value: Instant) {
        fun get(): Instant = value
    }
}
