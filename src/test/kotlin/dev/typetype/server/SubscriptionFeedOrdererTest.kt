package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.services.SubscriptionFeedOrderer
import dev.typetype.server.services.SubscriptionFeedSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionFeedOrdererTest {
    private val orderer = SubscriptionFeedOrderer()

    @Test
    fun `scheduled livestream follows normal chronology`() {
        val scheduled = video(2_000L, url = "scheduled")
        val recent = video(3_000L, url = "recent")

        val result = orderer.order(listOf(scheduled, recent), previous = null, refreshedAt = 10_000L)

        assertEquals(listOf("recent", "scheduled"), result.videos.map { it.url })
        assertEquals(emptyMap<String, Long>(), result.livePromotedAt)
    }

    @Test
    fun `scheduled to live transition is promoted once`() {
        val scheduled = video(2_000L, url = "live")
        val previous = snapshot(5_000L, listOf(scheduled))
        val live = video(-1L, url = "live", live = true)

        val promoted = orderer.order(listOf(video(8_000L), live), previous, refreshedAt = 10_000L)

        assertEquals("live", promoted.videos.first().url)
        assertEquals(10_000L, promoted.livePromotedAt["live"])
    }

    @Test
    fun `active livestream keeps its promotion while newer uploads pass it`() {
        val live = video(-1L, url = "live", live = true)
        val previous = snapshot(
            generatedAt = 10_000L,
            videos = listOf(live),
            livePromotedAt = mapOf("live" to 10_000L),
        )

        val result = orderer.order(listOf(live, video(12_000L, url = "new")), previous, refreshedAt = 20_000L)

        assertEquals(listOf("new", "live"), result.videos.map { it.url })
        assertEquals(10_000L, result.livePromotedAt["live"])
    }

    @Test
    fun `old snapshot gives an existing live one migration promotion`() {
        val live = video(-1L, url = "live", live = true)
        val previous = snapshot(7_000L, listOf(live))

        val result = orderer.order(listOf(live), previous, refreshedAt = 20_000L)

        assertEquals(7_000L, result.livePromotedAt["live"])
    }

    private fun snapshot(
        generatedAt: Long,
        videos: List<dev.typetype.server.models.VideoItem>,
        livePromotedAt: Map<String, Long> = emptyMap(),
    ) = SubscriptionFeedSnapshot(
        generation = 1L,
        generatedAt = generatedAt,
        stale = false,
        videos = videos,
        livePromotedAt = livePromotedAt,
    )
}
