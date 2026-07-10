package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSessionRegistryTest {
    @Test
    fun `capacity eviction removes oldest session`() {
        val registry = SabrSessionRegistry()
        val oldestKey = key("oldest")
        val idleKey = key("idle")
        val oldest = holder("oldest", Instant.EPOCH)
        val idle = holder("idle", Instant.EPOCH.plusSeconds(1))

        registry.put(oldestKey, oldest)
        registry.put(idleKey, idle)
        registry.ensureCapacity(2)

        assertNull(registry.get(oldestKey))
        assertSame(idle, registry.get(idleKey))
    }

    @Test
    fun `idle eviction removes stale sessions`() {
        val registry = SabrSessionRegistry()
        val staleKey = key("stale")
        val stale = holder("stale", Instant.EPOCH)

        registry.put(staleKey, stale)
        registry.evictIdle(Instant.EPOCH.plusSeconds(60))

        assertNull(registry.get(staleKey))
    }

    @Test
    fun `lookup by token returns matching playback session`() {
        val registry = SabrSessionRegistry()
        val key = key("playback")
        val holder = holder("playback", Instant.EPOCH)

        registry.put(key, holder)

        assertSame(holder, registry.lookupByToken("token-playback"))
    }

    @Test
    fun `healthy session remains reusable`() {
        val registry = SabrSessionRegistry()
        val key = key("healthy")
        val holder = holder("healthy", Instant.EPOCH)
        registry.put(key, holder)

        assertSame(holder, registry.getReusable(key))
        assertSame(holder, registry.lookupByToken("token-healthy"))
    }

    @Test
    fun `terminal session is removed instead of reused`() {
        val registry = SabrSessionRegistry()
        val key = key("terminal")
        val holder = holder("terminal", Instant.EPOCH)
        registry.put(key, holder)
        holder.failTerminal("upstream unauthorized")

        assertNull(registry.getReusable(key))
        assertNull(registry.lookupByToken("token-terminal"))
    }

    @Test
    fun `network failed session is removed instead of reused`() {
        val registry = SabrSessionRegistry()
        val key = key("network")
        val holder = holder("network", Instant.EPOCH)
        registry.put(key, holder)
        holder.recordNetworkFailure("connection reset")

        assertNull(registry.getReusable(key))
        assertNull(registry.lookupByToken("token-network"))
    }

    private fun key(id: String): SabrSessionKey = SabrSessionKey(id, "user", 140, null, 137, 0L)

    private fun holder(videoId: String, lastRequestAt: Instant): SabrSessionHolder {
        val key = key(videoId)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(true, true) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = info(videoId),
            audioFormat = format(140, isAudio = true),
            videoFormat = format(137, isAudio = false),
            sessionToken = "token-$videoId",
            key = key,
            lastRequestAt = lastRequestAt,
        )
    }

    private fun info(videoId: String): YoutubeSabrInfo {
        val info = mockk<YoutubeSabrInfo>()
        every { info.videoId } returns videoId
        return info
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        return format
    }
}
