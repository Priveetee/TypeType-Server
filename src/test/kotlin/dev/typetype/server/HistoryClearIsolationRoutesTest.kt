package dev.typetype.server

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val CLEAR_HISTORY_VIDEO_URL = "https://yt.test/watch?v=clear-history"
private const val CLEAR_HISTORY_CHANNEL_URL = "https://www.youtube.com/channel/UCclear"

class HistoryClearIsolationRoutesTest {
    private val history = HistoryService()
    private val progress = ProgressService()
    private val settings = SettingsService()
    private val subscriptions = SubscriptionsService()
    private val playlists = PlaylistService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() { TestDatabase.setup() } }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `DELETE history preserves unrelated user data`() = withApp {
        history.add(TEST_USER_ID, historyItem())
        progress.upsert(TEST_USER_ID, CLEAR_HISTORY_VIDEO_URL, 42_000L)
        settings.upsert(TEST_USER_ID, SettingsItem(defaultQuality = "720p"))
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = CLEAR_HISTORY_CHANNEL_URL, name = "Channel", avatarUrl = ""))
        playlists.create(TEST_USER_ID, PlaylistItem(name = "Saved"))

        val response = client.delete("/history") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(emptyList<HistoryItem>(), history.search(TEST_USER_ID, null, null, null, 20, 0).first)
        assertNull(progress.get(TEST_USER_ID, CLEAR_HISTORY_VIDEO_URL))
        assertEquals("720p", settings.get(TEST_USER_ID).defaultQuality)
        assertEquals(1, subscriptions.getAll(TEST_USER_ID).size)
        assertEquals(1, playlists.getAll(TEST_USER_ID).size)
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { install(ContentNegotiation) { json() }; routing { historyRoutes(history, auth) } }
        block()
    }

    private fun historyItem(): HistoryItem = HistoryItem(
        url = CLEAR_HISTORY_VIDEO_URL,
        title = "Video",
        thumbnail = "",
        channelName = "Channel",
        channelUrl = CLEAR_HISTORY_CHANNEL_URL,
        duration = 120L,
        progress = 0L,
    )
}
