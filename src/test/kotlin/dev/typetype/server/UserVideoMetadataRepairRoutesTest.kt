package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.UserVideoMetadataRepairService
import dev.typetype.server.services.VideoMetadataResolver
import dev.typetype.server.services.WatchLaterService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserVideoMetadataRepairRoutesTest {
    private val playlists = PlaylistService()
    private val watchLater = WatchLaterService()
    private val favorites = FavoritesService()
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val repair = UserVideoMetadataRepairService(VideoMetadataResolver(fakeStreamService()))

    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=abc123"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `list routes repair fallback video metadata`() = testApplication {
        val playlist = playlists.create(TEST_USER_ID, PlaylistItem(name = "Imported", description = ""))
        playlists.addVideo(TEST_USER_ID, playlist.id, fallbackVideo(VIDEO_URL))
        watchLater.add(TEST_USER_ID, WatchLaterItem(url = VIDEO_URL, title = "YouTube video abc123", thumbnail = "https://i.ytimg.com/vi/abc123/hqdefault.jpg", duration = 0L))
        favorites.add(TEST_USER_ID, VIDEO_URL)
        application {
            install(ContentNegotiation) { json() }
            routing {
                playlistRoutes(playlists, auth, repair)
                watchLaterRoutes(watchLater, auth, repair)
                favoritesRoutes(favorites, auth, repair)
            }
        }

        val playlistBody = client.get("/playlists") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        val watchLaterBody = client.get("/watch-later") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        val favoritesBody = client.get("/favorites") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()

        assertTrue(playlistBody.contains("Resolved abc123"))
        assertTrue(watchLaterBody.contains("Resolved abc123"))
        assertTrue(favoritesBody.contains("Resolved abc123"))
        assertTrue(favoritesBody.contains("\"thumbnail\":\"https://thumb.test/abc123.jpg\""))
    }

    private fun fallbackVideo(url: String): PlaylistVideoItem = PlaylistVideoItem(
        url = url,
        title = "YouTube video abc123",
        thumbnail = "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
        duration = 0L,
    )

    private fun fakeStreamService(): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = ExtractionResult.Success(stream(url))
    }

    private fun stream(url: String): StreamResponse = StreamResponse(
        id = "abc123", title = "Resolved abc123", uploaderName = "Channel", uploaderUrl = "https://www.youtube.com/channel/UC1", uploaderAvatarUrl = "https://avatar.test/uc1.jpg",
        thumbnailUrl = "https://thumb.test/abc123.jpg", description = "", duration = 120L, viewCount = 10L, likeCount = 0L, dislikeCount = 0L, uploadDate = "", uploaded = 1L,
        uploaderSubscriberCount = 0L, uploaderVerified = false, category = "", license = "", visibility = "", tags = emptyList(), streamType = "video", isShortFormContent = false,
        requiresMembership = false, startPosition = 0L, streamSegments = emptyList(), hlsUrl = "", dashMpdUrl = "", videoStreams = emptyList(), audioStreams = emptyList(),
        originalAudioTrackId = null, preferredDefaultAudioTrackId = null, videoOnlyStreams = emptyList(), subtitles = emptyList(), previewFrames = emptyList(), sponsorBlockSegments = emptyList(), relatedStreams = emptyList(),
    )
}
