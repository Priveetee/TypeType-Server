package dev.typetype.server

import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.services.CommentService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommentFieldsTest {

    private val commentService: CommentService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { commentRoutes(commentService) }
        }
        block()
    }

    @Test
    fun `GET comments serializes commentsDisabled textualLikeCount uploaderVerified`() = withApp {
        val item = testCommentItem(textualLikeCount = "3.3K", uploaderVerified = true)
        coEvery { commentService.getComments(any(), any()) } returns
            ExtractionResult.Success(CommentsPageResponse(listOf(item), null, commentsDisabled = true))
        val body = client.get("/comments?url=https://youtube.com/watch?v=test").bodyAsText()
        assertTrue(body.contains("\"commentsDisabled\":true"))
        assertTrue(body.contains("\"textualLikeCount\":\"3.3K\""))
        assertTrue(body.contains("\"uploaderVerified\":true"))
    }
}
