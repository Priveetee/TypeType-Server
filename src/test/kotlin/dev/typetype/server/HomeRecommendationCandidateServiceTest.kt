package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCandidateService
import dev.typetype.server.services.HomeRecommendationPoolMode
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationSignalContext
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.TrendingService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationCandidateServiceTest {
    private val subscriptionFeedService: SubscriptionFeedService = mockk()
    private val subscriptionShortsFeedService: SubscriptionShortsFeedService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val streamService: StreamService = mockk()
    private val service = HomeRecommendationCandidateService(
        subscriptionFeedService, subscriptionShortsFeedService, trendingService, searchService, streamService,
    )

    @BeforeEach
    fun setup() {
        coEvery { subscriptionFeedService.getCachedFeed(any(), any(), any()) } returns SubscriptionFeedResponse(emptyList(), null)
        coEvery { subscriptionShortsFeedService.getBlendedFeed(any(), any(), any(), any()) } returns SubscriptionFeedResponse(emptyList(), null)
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Failure("none")
    }

    @Test
    fun `fast mode builds discovery from subscription related streams`() = runTest {
        val seed = video("s1", "seed")
        val related = video("r1", "related")
        coEvery { subscriptionFeedService.getCachedFeed(any(), any(), any()) } returns SubscriptionFeedResponse(listOf(seed), null)
        coEvery { streamService.getStreamInfo(seed.url) } returns ExtractionResult.Success(stream(seed.url, listOf(related)))
        val pool = service.fetchCandidates("u", 0, profile(), HomeRecommendationPoolMode.FAST)
        assertTrue(pool.discovery.any { it.video.id == "r1" && it.source == HomeRecommendationSourceTag.DISCOVERY_THEME })
    }

    @Test
    fun `favorite seeds add exploration-tagged related discovery`() = runTest {
        val favoriteSeed = video("f1", "favorite")
        val related = video("rf1", "related")
        coEvery { streamService.getStreamInfo(favoriteSeed.url) } returns ExtractionResult.Success(stream(favoriteSeed.url, listOf(related)))
        val signalContext = HomeRecommendationSignalContext(favoriteUrls = listOf(favoriteSeed.url))
        val pool = service.fetchCandidates("u", 0, profile(), HomeRecommendationPoolMode.FAST, signalContext)
        assertTrue(pool.discovery.any { it.video.id == "rf1" && it.source == HomeRecommendationSourceTag.DISCOVERY_EXPLORATION })
    }

    @Test
    fun `no subscription or favorite seeds keeps discovery empty`() = runTest {
        val pool = service.fetchCandidates("u", 0, profile(), HomeRecommendationPoolMode.FAST)
        assertTrue(pool.discovery.isEmpty())
    }

    private fun profile(): HomeRecommendationProfile = HomeRecommendationProfile(
        seenUrls = emptySet(), blockedVideos = emptySet(), blockedChannels = emptySet(),
        feedbackBlockedVideos = emptySet(), feedbackBlockedChannels = emptySet(),
        subscriptionChannels = emptySet(), favoriteUrls = emptySet(), watchLaterUrls = emptySet(),
        keywordAffinity = emptySet(), themeTokens = emptySet(), themeQueries = emptyList(),
        channelInterest = emptyMap(), topicInterest = emptyMap(),
    )

    private fun stream(seedUrl: String, related: List<VideoItem>): StreamResponse = StreamResponse(
        id = "id", title = "t", uploaderName = "u", uploaderUrl = "c", uploaderAvatarUrl = "", thumbnailUrl = "",
        description = "", duration = 1, viewCount = 0, likeCount = 0, dislikeCount = 0, uploadDate = "", uploaded = 0,
        uploaderSubscriberCount = 0, uploaderVerified = false, category = "", license = "", visibility = "",
        tags = emptyList(), streamType = "video_stream", isShortFormContent = false, requiresMembership = false,
        startPosition = 0, streamSegments = emptyList(), hlsUrl = "", dashMpdUrl = "", videoStreams = emptyList(),
        audioStreams = emptyList(), originalAudioTrackId = null, preferredDefaultAudioTrackId = null,
        videoOnlyStreams = emptyList(), subtitles = emptyList(), previewFrames = emptyList(),
        sponsorBlockSegments = emptyList(), relatedStreams = related, publishedAt = 0,
    )

    private fun video(id: String, title: String): VideoItem = VideoItem(
        id = id, title = title, url = "https://yt.com/v/$id", thumbnailUrl = "", uploaderName = "channel",
        uploaderUrl = "https://yt.com/c/channel", uploaderAvatarUrl = "", duration = 60, viewCount = 0,
        uploadDate = "", uploaded = System.currentTimeMillis(), streamType = "video_stream", isShortFormContent = false,
        uploaderVerified = false, shortDescription = null,
    )
}
