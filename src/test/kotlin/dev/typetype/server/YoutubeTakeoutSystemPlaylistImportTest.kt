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

class YoutubeTakeoutSystemPlaylistImportTest {
    private val playlists = PlaylistService()
    private val watchLater = WatchLaterService()
    private val favorites = FavoritesService()
    private val signal = YoutubeTakeoutSignalImportService(favorites, watchLater, HistoryService())
    private val importer = YoutubeTakeoutImporterService(SubscriptionsService(), playlists, signal)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `commit maps takeout system playlists to system collections`() = runBlocking {
        importer.commit(TEST_USER_ID, parsed(), YoutubeTakeoutCommitPlan(true, true, true, true, true, false))

        assertEquals(emptyList<PlaylistItem>(), playlists.getAll(TEST_USER_ID))
        assertEquals(listOf("https://www.youtube.com/watch?v=watch1"), watchLater.getAll(TEST_USER_ID).map { it.url })
        assertEquals(listOf("https://www.youtube.com/watch?v=like1"), favorites.getAll(TEST_USER_ID).map { it.videoUrl })
        assertEquals(1_700_000_000_000L, watchLater.getAll(TEST_USER_ID).single().addedAt)
        assertEquals(1_700_000_100_000L, favorites.getAll(TEST_USER_ID).single().favoritedAt)
    }

    private fun parsed(): YoutubeTakeoutParsedData = YoutubeTakeoutParsedData(
        subscriptions = emptyList(),
        playlists = listOf(PlaylistItem(id = "WL", name = "Watch later"), PlaylistItem(id = "LL", name = "Liked videos")),
        playlistItems = mapOf("Watch later" to listOf(video("watch1", 1_700_000_000_000L))),
        favorites = listOf(FavoriteItem(videoUrl = "https://www.youtube.com/watch?v=like1", favoritedAt = 1_700_000_100_000L)),
        watchLater = listOf(video("watch1", 1_700_000_000_000L)),
        history = emptyList(),
        warnings = emptyList(),
        errors = emptyList(),
    )

    private fun video(id: String, addedAt: Long): PlaylistVideoItem = PlaylistVideoItem(
        url = "https://www.youtube.com/watch?v=$id",
        title = "Video $id",
        thumbnail = "",
        duration = 0L,
        addedAt = addedAt,
    )
}
