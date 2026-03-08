package dev.typetype.server

import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.services.CommentService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommentRoutesTest {

    private val commentService: CommentService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { commentRoutes(commentService) }
        }
        block()
    }

    @Test
    fun `GET comments without url returns 400`() = withApp {
        val response = client.get("/comments")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET comments returns 200 on Success`() = withApp {
        coEvery { commentService.getComments(any(), any()) } returns
            ExtractionResult.Success(CommentsPageResponse(comments = emptyList(), nextpage = null))
        val response = client.get("/comments?url=https://youtube.com/watch?v=test")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET comments returns 422 on Failure`() = withApp {
        coEvery { commentService.getComments(any(), any()) } returns
            ExtractionResult.Failure("error")
        val response = client.get("/comments?url=https://youtube.com/watch?v=test")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET comments returns 400 on BadRequest`() = withApp {
        coEvery { commentService.getComments(any(), any()) } returns
            ExtractionResult.BadRequest("bad")
        val response = client.get("/comments?url=https://youtube.com/watch?v=test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
