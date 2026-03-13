package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.StreamService
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

class ManifestRoutesTest {

    private val streamService: StreamService = mockk()
    private val manifestService = ManifestService(streamService)
    private val nativeManifestService = NativeManifestService()
    private val hlsManifestService: HlsManifestService = mockk()

    @Test
    fun `GET streams manifest without url returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { manifestRoutes(manifestService, nativeManifestService, hlsManifestService) }
        }
        val response = client.get("/streams/manifest")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET streams manifest with streams returns 200 and dash+xml`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse())
        application {
            install(ContentNegotiation) { json() }
            routing { manifestRoutes(manifestService, nativeManifestService, hlsManifestService) }
        }
        val response = client.get("/streams/manifest?url=https://youtube.com/watch?v=test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"]?.contains("dash+xml") == true)
        assertTrue(response.bodyAsText().contains("<MPD"))
    }

    @Test
    fun `GET streams manifest with no compatible streams returns 422`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(
                testStreamResponse(videoOnlyStreams = emptyList(), audioStreams = emptyList())
            )
        application {
            install(ContentNegotiation) { json() }
            routing { manifestRoutes(manifestService, nativeManifestService, hlsManifestService) }
        }
        val response = client.get("/streams/manifest?url=https://youtube.com/watch?v=empty")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET streams native-manifest without url returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { manifestRoutes(manifestService, nativeManifestService, hlsManifestService) }
        }
        val response = client.get("/streams/native-manifest")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
