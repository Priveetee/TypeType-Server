package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFilterOption
import dev.typetype.server.models.SearchFiltersResponse
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
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchFiltersRoutesTest {
    private val searchService: SearchService = mockk()

    @Test
    fun `GET search filters returns supported filters`() = withApp {
        coEvery { searchService.filters(0) } returns ExtractionResult.Success(
            SearchFiltersResponse(
                contentFilters = listOf(SearchFilterOption(value = "type|1|Videos", label = "Type: Videos")),
                sortFilters = listOf(SearchFilterOption(value = "sort|2|Upload date", label = "Sort: Upload date")),
            )
        )
        val response = client.get("/search/filters?service=0")
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("contentFilters"))
        assertTrue(body.contains("Upload date"))
    }

    @Test
    fun `GET search passes selected filters`() = withApp {
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(
            SearchPageResponse(items = emptyList(), nextpage = null, searchSuggestion = null, isCorrectedSearch = false)
        )
        val response = client.get("/search?q=test&service=0&contentFilter=content&sortFilter=sort")
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { searchService.search("test", 0, null, "content", "sort") }
    }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { searchRoutes(searchService) }
        }
        block()
    }
}
