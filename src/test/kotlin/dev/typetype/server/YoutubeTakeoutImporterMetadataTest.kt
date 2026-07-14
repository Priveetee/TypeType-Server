package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.VideoMetadataResolver
import dev.typetype.server.services.WatchLaterService
import dev.typetype.server.services.YoutubeTakeoutImporterService
import dev.typetype.server.services.YoutubeTakeoutSignalImportService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YoutubeTakeoutImporterMetadataTest {
    private val subscriptions = SubscriptionsService()
    private val playlists = PlaylistService()
    private val favorites = FavoritesService()
    private val watchLater = WatchLaterService()
    private val history = HistoryService()
    private val signalImport = YoutubeTakeoutSignalImportService(favorites, watchLater, history)
    private val importer = YoutubeTakeoutImporterService(subscriptions, playlists, signalImport, metadataResolver = VideoMetadataResolver(fakeStreamService()))

    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=abc123"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `commit enriches takeout list videos and favorites before persisting`() = runBlocking {
        importer.commit(TEST_USER_ID, parsed(), YoutubeTakeoutCommitPlan(true, true, true, true, true, false))

        val playlistId = playlists.getAll(TEST_USER_ID).single().id
        val playlistVideo = playlists.getById(TEST_USER_ID, playlistId)?.videos?.single()
        val watchLaterVideo = watchLater.getAll(TEST_USER_ID).single()
        val favorite = favorites.getAll(TEST_USER_ID).single()
        assertEquals("Resolved abc123", playlistVideo?.title)
        assertEquals("Resolved abc123", watchLaterVideo.title)
        assertEquals("Resolved abc123", favorite.title)
        assertEquals("https://thumb.test/abc123.jpg", favorite.thumbnail)
    }

    private fun parsed(): YoutubeTakeoutParsedData = YoutubeTakeoutParsedData(
        subscriptions = emptyList(),
        playlists = listOf(PlaylistItem(id = "PL1", name = "Imported")),
        playlistItems = mapOf("PL1" to listOf(fallbackVideo())),
        favorites = listOf(FavoriteItem(videoUrl = VIDEO_URL)),
        watchLater = listOf(fallbackVideo()),
        history = emptyList(),
        warnings = emptyList(),
        errors = emptyList(),
    )

    private fun fallbackVideo(): PlaylistVideoItem = PlaylistVideoItem(
        url = VIDEO_URL,
        title = "YouTube video abc123",
        thumbnail = "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
        duration = 0L,
    )

    private fun fakeStreamService(): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = ExtractionResult.Success(stream())
    }

    private fun stream(): StreamResponse = StreamResponse(
        id = "abc123", title = "Resolved abc123", uploaderName = "Channel", uploaderUrl = "https://www.youtube.com/channel/UC1", uploaderAvatarUrl = "https://avatar.test/uc1.jpg",
        thumbnailUrl = "https://thumb.test/abc123.jpg", description = "", duration = 120L, viewCount = 10L, likeCount = 0L, dislikeCount = 0L, uploadDate = "", uploaded = 1L,
        uploaderSubscriberCount = 0L, uploaderVerified = false, category = "", license = "", visibility = "", tags = emptyList(), streamType = "video", isShortFormContent = false,
        requiresMembership = false, startPosition = 0L, streamSegments = emptyList(), hlsUrl = "", dashMpdUrl = "", videoStreams = emptyList(), audioStreams = emptyList(),
        originalAudioTrackId = null, preferredDefaultAudioTrackId = null, videoOnlyStreams = emptyList(), subtitles = emptyList(), previewFrames = emptyList(), sponsorBlockSegments = emptyList(), relatedStreams = emptyList(),
    )
}
