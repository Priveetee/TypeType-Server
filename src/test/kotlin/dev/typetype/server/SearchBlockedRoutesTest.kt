package dev.typetype.server

import dev.typetype.server.models.ChannelResultItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PlaylistResultItem
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.SearchService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchBlockedRoutesTest {
    private val searchService: SearchService = mockk()
    private val blockedService = BlockedService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `search filters videos channels and playlists from blocked channels`() = testApplication {
        blockedService.addChannel(TEST_USER_ID, "https://youtube.com/@blocked", "Blocked Channel")
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(response())
        application { install(ContentNegotiation) { json() }; routing { searchRoutes(searchService, authService = auth, blockedService = blockedService) } }

        val response = client.get("/search?q=test&service=0") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(body.contains("Blocked Video"))
        assertFalse(body.contains("Blocked Channel"))
        assertFalse(body.contains("Blocked Playlist"))
        assertTrue(body.contains("Allowed Video"))
        assertTrue(body.contains("Allowed Channel"))
        assertTrue(body.contains("Allowed Playlist"))
    }

    private fun response(): SearchPageResponse = SearchPageResponse(
        items = listOf(
            testVideoItem().copy(title = "Blocked Video", uploaderName = "Blocked Channel", uploaderUrl = "https://youtube.com/@blocked"),
            testVideoItem().copy(title = "Allowed Video", uploaderName = "Allowed Channel", uploaderUrl = "https://youtube.com/@allowed"),
        ),
        nextpage = null,
        searchSuggestion = null,
        isCorrectedSearch = false,
        channels = listOf(
            channel("Blocked Channel", "https://youtube.com/@blocked"),
            channel("Allowed Channel", "https://youtube.com/@allowed"),
        ),
        playlists = listOf(
            playlist("Blocked Playlist", "Blocked Channel"),
            playlist("Allowed Playlist", "Allowed Channel"),
        ),
    )

    private fun channel(name: String, url: String): ChannelResultItem = ChannelResultItem("id-$name", name, url, "", "", 0, 0, false)

    private fun playlist(title: String, uploader: String): PlaylistResultItem =
        PlaylistResultItem("id-$title", title, "https://youtube.com/playlist?list=$title", "", uploader, 1, "normal")
}
