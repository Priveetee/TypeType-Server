package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationPoolBuilder
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.HomeRecommendationTaggedVideo
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
            tagged(video("seen", "sub"), HomeRecommendationSourceTag.SUBSCRIPTION),
            tagged(video("blocked", "sub"), HomeRecommendationSourceTag.SUBSCRIPTION),
            tagged(video("ok", "sub", title = "music review"), HomeRecommendationSourceTag.SUBSCRIPTION),
            tagged(video("chblocked", "blocked"), HomeRecommendationSourceTag.SUBSCRIPTION),
        )
        val discovery = listOf(
            tagged(video("ok", "sub"), HomeRecommendationSourceTag.DISCOVERY_TRENDING),
            tagged(video("new", "x"), HomeRecommendationSourceTag.DISCOVERY_THEME),
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
            tagged(video("plain", "a", title = "daily vlog"), HomeRecommendationSourceTag.SUBSCRIPTION),
            tagged(video("match", "a", title = "music review"), HomeRecommendationSourceTag.SUBSCRIPTION),
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
            tagged(video("live1", "a", title = "Breaking is live now"), HomeRecommendationSourceTag.DISCOVERY_TRENDING),
            tagged(video("normal1", "b", title = "Weekly tech roundup"), HomeRecommendationSourceTag.DISCOVERY_THEME),
            tagged(video("live2", "c", title = "DIRECT: match day"), HomeRecommendationSourceTag.DISCOVERY_EXPLORATION),
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, emptyList(), discovery)
        assertEquals(1, pool.discovery.size)
        assertTrue(pool.discovery.first().url.endsWith("/normal1"))
    }

    private fun tagged(video: VideoItem, source: HomeRecommendationSourceTag): HomeRecommendationTaggedVideo =
        HomeRecommendationTaggedVideo(video = video, source = source)
}
