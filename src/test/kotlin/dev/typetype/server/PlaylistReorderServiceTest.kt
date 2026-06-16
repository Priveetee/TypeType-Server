package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistReorderResult
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.services.PlaylistService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlaylistReorderServiceTest {
    private val service = PlaylistService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `reorder updates playlist positions`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, video("https://yt.com/1"))
        service.addVideo(TEST_USER_ID, playlist.id, video("https://yt.com/2"))

        val result = service.reorder(TEST_USER_ID, playlist.id, listOf("https://yt.com/2", "https://yt.com/1"))
        val videos = service.getById(TEST_USER_ID, playlist.id)?.videos.orEmpty()

        assertEquals(PlaylistReorderResult.Success, result)
        assertEquals(listOf("https://yt.com/2", "https://yt.com/1"), videos.map { it.url })
        assertEquals(listOf(0, 1), videos.map { it.position })
    }

    @Test
    fun `reorder rejects missing playlist videos`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, video("https://yt.com/1"))

        val result = service.reorder(TEST_USER_ID, playlist.id, listOf("https://yt.com/other"))

        assertTrue(result is PlaylistReorderResult.InvalidOrder)
    }

    @Test
    fun `addVideo persists added and published dates`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        val added = service.addVideo(TEST_USER_ID, playlist.id, video("https://yt.com/1", publishedAt = 1234L))
        val stored = service.getById(TEST_USER_ID, playlist.id)?.videos?.single()

        assertTrue(added.addedAt > 0L)
        assertEquals(1234L, stored?.publishedAt)
        assertTrue((stored?.addedAt ?: 0L) > 0L)
    }

    private fun video(url: String, publishedAt: Long = -1L): PlaylistVideoItem =
        PlaylistVideoItem(url = url, title = "T", thumbnail = "", duration = 10L, publishedAt = publishedAt)
}
