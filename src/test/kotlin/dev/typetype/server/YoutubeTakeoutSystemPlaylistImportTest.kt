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

    @Test
    fun `commit preserves imported playlist order and added dates`() = runBlocking {
        val newer = video("newer", 1_700_000_100_000L)
        val older = video("older", 1_700_000_000_000L)
        val parsed = YoutubeTakeoutParsedData(
            subscriptions = emptyList(),
            playlists = listOf(PlaylistItem(id = "PL123456", name = "Imported")),
            playlistItems = mapOf("PL123456" to listOf(newer, older)),
            favorites = emptyList(),
            watchLater = emptyList(),
            history = emptyList(),
            warnings = emptyList(),
            errors = emptyList(),
        )

        importer.commit(TEST_USER_ID, parsed, YoutubeTakeoutCommitPlan(false, true, true, false, false, false))

        val playlistId = playlists.getAll(TEST_USER_ID).single().id
        val imported = playlists.getById(TEST_USER_ID, playlistId)?.videos.orEmpty()
        assertEquals(listOf(newer.url, older.url), imported.map { it.url })
        assertEquals(listOf(newer.addedAt, older.addedAt), imported.map { it.addedAt })
    }

    @Test
    fun `commit returns imported favorites newest first`() = runBlocking {
        val parsed = YoutubeTakeoutParsedData(
            subscriptions = emptyList(),
            playlists = emptyList(),
            playlistItems = emptyMap(),
            favorites = listOf(
                FavoriteItem(videoUrl = "https://www.youtube.com/watch?v=older", favoritedAt = 1_700_000_000_000L),
                FavoriteItem(videoUrl = "https://www.youtube.com/watch?v=newer", favoritedAt = 1_700_000_100_000L),
            ),
            watchLater = emptyList(),
            history = emptyList(),
            warnings = emptyList(),
            errors = emptyList(),
        )

        importer.commit(TEST_USER_ID, parsed, YoutubeTakeoutCommitPlan(false, false, false, true, false, false))

        assertEquals(listOf("newer", "older"), favorites.getAll(TEST_USER_ID).map { it.videoUrl.substringAfter("v=") })
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
