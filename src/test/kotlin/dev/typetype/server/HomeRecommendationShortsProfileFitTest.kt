package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationShortsProfileFit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsProfileFitTest {
    @Test
    fun `profile fit favors subscribed channel over generic viral`() {
        val profile = HomeRecommendationProfile(
            seenUrls = emptySet(),
            blockedVideos = emptySet(),
            blockedChannels = emptySet(),
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = setOf("https://yt.com/c/linux"),
            favoriteUrls = emptySet(),
            watchLaterUrls = emptySet(),
            keywordAffinity = setOf("linux", "kernel", "terminal"),
            themeTokens = emptySet(),
            themeQueries = emptyList(),
        )
        val subscribed = video("linux terminal tips", "https://yt.com/c/linux")
        val generic = video("random viral facts", "https://yt.com/c/random")
        val a = HomeRecommendationShortsProfileFit.score(subscribed, profile)
        val b = HomeRecommendationShortsProfileFit.score(generic, profile)
        assertTrue(a > b)
    }

    private fun video(title: String, uploaderUrl: String): VideoItem = VideoItem(
        id = title,
        title = title,
        url = "https://yt.com/v/${title.hashCode()}",
        thumbnailUrl = "",
        uploaderName = "u",
        uploaderUrl = uploaderUrl,
        uploaderAvatarUrl = "",
        duration = 40,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = true,
        uploaderVerified = false,
        shortDescription = null,
    )
}
