package dev.typetype.server

import dev.typetype.server.models.ChannelResultItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.services.SearchService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchChannelsRoutesTest {
    private val searchService: SearchService = mockk()

    @Test
    fun `GET search returns channel results`() = withApp {
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(
            SearchPageResponse(
                items = emptyList(),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
                channels = listOf(channel()),
            )
        )
        val response = client.get("/search?q=test&service=0")
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"channels\""))
        assertTrue(body.contains("\"subscriberCount\":42"))
    }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { searchRoutes(searchService) }
        }
        block()
    }

    private fun channel(): ChannelResultItem = ChannelResultItem(
        id = "channel-id",
        name = "Channel",
        url = "https://youtube.com/channel/channel-id",
        thumbnailUrl = "https://img.youtube.com/channel.jpg",
        description = "Description",
        subscriberCount = 42L,
        streamCount = 7L,
        isVerified = true,
    )
}
