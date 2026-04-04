package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCandidateService
import dev.typetype.server.services.HomeRecommendationPoolMode
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.TrendingService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationCandidateServiceTest {
    private val subscriptionFeedService: SubscriptionFeedService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val service = HomeRecommendationCandidateService(subscriptionFeedService, trendingService, searchService)

    @BeforeEach
    fun setup() {
        coEvery { subscriptionFeedService.getCachedFeed(any(), any(), any()) } returns SubscriptionFeedResponse(emptyList(), null)
        coEvery { searchService.search(any(), any(), any()) } returns ExtractionResult.Success(
            SearchPageResponse(emptyList(), null, null, false),
        )
    }

    @Test
    fun `fast mode keeps discovery via exploration fallback when theme filtering is too strict`() = runTest {
        coEvery { trendingService.getTrending(0) } returns ExtractionResult.Success(
            listOf(video(id = "d1", title = "Football highlights", channel = "SportsHub")),
        )
        val profile = profile(themeTokens = setOf("linux"), themeQueries = listOf("linux kernel"))
        val pool = service.fetchCandidates("u", 0, profile, HomeRecommendationPoolMode.FAST)
        assertTrue(pool.discovery.any { it.video.id == "d1" })
    }

    @Test
    fun `fast mode uses exploration queries when themed queries are empty`() = runTest {
        coEvery { trendingService.getTrending(0) } returns ExtractionResult.Success(emptyList())
        coEvery { searchService.search(any(), any(), any()) } answers {
            val query = firstArg<String>()
            val items = if (query == "trending videos") {
                listOf(video(id = "e1", title = "Daily world update", channel = "NewsNow"))
            } else {
                emptyList()
            }
            ExtractionResult.Success(SearchPageResponse(items, null, null, false))
        }
        val pool = service.fetchCandidates("u", 0, profile(themeTokens = emptySet(), themeQueries = emptyList()), HomeRecommendationPoolMode.FAST)
        assertTrue(pool.discovery.any { it.video.id == "e1" })
    }

    @Test
    fun `candidate tagging preserves source attribution`() = runTest {
        coEvery { trendingService.getTrending(0) } returns ExtractionResult.Success(
            listOf(video(id = "t1", title = "Trend now", channel = "TrendHub")),
        )
        val pool = service.fetchCandidates("u", 0, profile(themeTokens = emptySet(), themeQueries = emptyList()), HomeRecommendationPoolMode.FAST)
        assertTrue(pool.discovery.any { it.video.id == "t1" && it.source == HomeRecommendationSourceTag.DISCOVERY_TRENDING })
    }

    private fun profile(themeTokens: Set<String>, themeQueries: List<String>): HomeRecommendationProfile = HomeRecommendationProfile(
        seenUrls = emptySet(),
        blockedVideos = emptySet(),
        blockedChannels = emptySet(),
        feedbackBlockedVideos = emptySet(),
        feedbackBlockedChannels = emptySet(),
        subscriptionChannels = emptySet(),
        favoriteUrls = emptySet(),
        watchLaterUrls = emptySet(),
        keywordAffinity = emptySet(),
        themeTokens = themeTokens,
        themeQueries = themeQueries,
        channelInterest = emptyMap(),
        topicInterest = emptyMap(),
    )

    private fun video(id: String, title: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = title,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 120,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
}
