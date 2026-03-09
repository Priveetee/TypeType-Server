package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamSegmentItem
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.StreamService
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

class StreamFieldsTest {

    private val streamService: StreamService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        block()
    }

    @Test
    fun `GET streams serializes streamType`() = withApp {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse().copy(streamType = "live_stream"))
        val body = client.get("/streams?url=https://youtube.com/watch?v=test").bodyAsText()
        assertTrue(body.contains("\"streamType\":\"live_stream\""))
    }

    @Test
    fun `GET streams serializes isShortFormContent requiresMembership startPosition`() = withApp {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(
                testStreamResponse().copy(
                    isShortFormContent = true,
                    requiresMembership = true,
                    startPosition = 42L,
                )
            )
        val body = client.get("/streams?url=https://youtube.com/shorts/test").bodyAsText()
        assertTrue(body.contains("\"isShortFormContent\":true"))
        assertTrue(body.contains("\"requiresMembership\":true"))
        assertTrue(body.contains("\"startPosition\":42"))
    }

    @Test
    fun `GET streams serializes streamSegments`() = withApp {
        val segment = StreamSegmentItem(
            title = "Intro",
            startTimeSeconds = 0,
            channelName = null,
            url = null,
            previewUrl = null,
        )
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse().copy(streamSegments = listOf(segment)))
        val body = client.get("/streams?url=https://youtube.com/watch?v=test").bodyAsText()
        assertTrue(body.contains("\"streamSegments\""))
        assertTrue(body.contains("\"title\":\"Intro\""))
        assertTrue(body.contains("\"startTimeSeconds\":0"))
    }

    @Test
    fun `GET streams serializes audioLocale in audioStreams`() = withApp {
        val audio = testAudioStream().copy(audioLocale = "en")
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(audioStreams = listOf(audio)))
        val body = client.get("/streams?url=https://youtube.com/watch?v=test").bodyAsText()
        assertTrue(body.contains("\"audioLocale\":\"en\""))
    }
}
