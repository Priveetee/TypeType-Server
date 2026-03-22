package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationPoolBuilder
import dev.typetype.server.services.HomeRecommendationProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationPoolBuilderTest {

    private fun video(id: String, channel: String, title: String = id): VideoItem = VideoItem(
        id = id,
        title = title,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 1,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )

    @Test
    fun `pool builder excludes seen and blocked and dedups across sources`() {
        val profile = HomeRecommendationProfile(
            seenUrls = setOf("https://yt.com/v/seen"),
            blockedVideos = setOf("https://yt.com/v/blocked"),
            blockedChannels = setOf("https://yt.com/c/blocked"),
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = setOf("https://yt.com/c/sub"),
            favoriteUrls = emptySet(),
            watchLaterUrls = emptySet(),
            keywordAffinity = setOf("music"),
            themeTokens = emptySet(),
            themeQueries = emptyList(),
            channelInterest = emptyMap(),
            topicInterest = emptyMap(),
        )
        val subscriptions = listOf(
            video("seen", "sub"),
            video("blocked", "sub"),
            video("ok", "sub", title = "music review"),
            video("chblocked", "blocked"),
        )
        val discovery = listOf(
            video("ok", "sub"),
            video("new", "x"),
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, subscriptions, discovery)
        assertEquals(1, pool.subscriptions.size)
        assertEquals("https://yt.com/v/ok", pool.subscriptions.first().url)
        assertEquals(1, pool.discovery.size)
        assertEquals("https://yt.com/v/new", pool.discovery.first().url)
    }

    @Test
    fun `pool builder ranks title keyword matches higher`() {
        val profile = HomeRecommendationProfile(
            seenUrls = emptySet(),
            blockedVideos = emptySet(),
            blockedChannels = emptySet(),
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = emptySet(),
            favoriteUrls = emptySet(),
            watchLaterUrls = emptySet(),
            keywordAffinity = setOf("music"),
            themeTokens = emptySet(),
            themeQueries = emptyList(),
            channelInterest = emptyMap(),
            topicInterest = emptyMap(),
        )
        val subscriptions = listOf(
            video("plain", "a", title = "daily vlog"),
            video("match", "a", title = "music review"),
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, subscriptions, emptyList())
        assertTrue(pool.subscriptions.first().url.endsWith("/match"))
    }

    @Test
    fun `pool builder drops live-like discovery candidates`() {
        val profile = HomeRecommendationProfile(
            seenUrls = emptySet(),
            blockedVideos = emptySet(),
            blockedChannels = emptySet(),
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = emptySet(),
            favoriteUrls = emptySet(),
            watchLaterUrls = emptySet(),
            keywordAffinity = emptySet(),
            themeTokens = emptySet(),
            themeQueries = emptyList(),
            channelInterest = emptyMap(),
            topicInterest = emptyMap(),
        )
        val discovery = listOf(
            video("live1", "a", title = "Breaking is live now"),
            video("normal1", "b", title = "Weekly tech roundup"),
            video("live2", "c", title = "DIRECT: match day"),
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, emptyList(), discovery)
        assertEquals(1, pool.discovery.size)
        assertTrue(pool.discovery.first().url.endsWith("/normal1"))
    }
}
