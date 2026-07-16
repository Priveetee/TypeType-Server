package dev.typetype.server

import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.SubscriptionsService
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
    private val importer = YoutubeTakeoutImporterService(
        subscriptions,
        playlists,
        YoutubeTakeoutSignalImportService(favorites, watchLater, history),
    )

    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=abc123"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `commit persists parsed takeout metadata without stream extraction`() = runBlocking {
        importer.commit(TEST_USER_ID, parsed(), YoutubeTakeoutCommitPlan(true, true, true, true, true, false))

        val playlistId = playlists.getAll(TEST_USER_ID).single().id
        assertEquals("Takeout title", playlists.getById(TEST_USER_ID, playlistId)?.videos?.single()?.title)
        assertEquals("Takeout title", watchLater.getAll(TEST_USER_ID).single().title)
        assertEquals("YouTube video abc123", favorites.getAll(TEST_USER_ID).single().title)
    }

    private fun parsed() = YoutubeTakeoutParsedData(
        subscriptions = emptyList(),
        playlists = listOf(PlaylistItem(id = "PL1", name = "Imported")),
        playlistItems = mapOf("PL1" to listOf(video())),
        favorites = listOf(FavoriteItem(videoUrl = VIDEO_URL)),
        watchLater = listOf(video()),
        history = emptyList(),
        warnings = emptyList(),
        errors = emptyList(),
    )

    private fun video() = PlaylistVideoItem(
        url = VIDEO_URL,
        title = "Takeout title",
        thumbnail = "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
        duration = 0L,
    )
}
