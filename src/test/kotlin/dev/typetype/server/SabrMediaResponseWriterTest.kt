package dev.typetype.server

import dev.typetype.server.routes.respondSabrMediaBytes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrMediaResponseWriterTest {
    @Test
    fun `sabr media bytes include fixed length and ranges`() = testApplication {
        installApp()

        val response = client.get("/media")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        assertEquals("10", response.headers[HttpHeaders.ContentLength])
        assertArrayEquals(ByteArray(10) { it.toByte() }, response.bodyAsBytes())
    }

    @Test
    fun `sabr media bytes honor byte ranges`() = testApplication {
        installApp()

        val response = client.get("/media") { header(HttpHeaders.Range, "bytes=2-5") }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        assertEquals("bytes 2-5/10", response.headers[HttpHeaders.ContentRange])
        assertEquals("4", response.headers[HttpHeaders.ContentLength])
        assertArrayEquals(byteArrayOf(2, 3, 4, 5), response.bodyAsBytes())
    }

    private fun ApplicationTestBuilder.installApp(): Unit = application {
        routing {
            get("/media") {
                call.respondSabrMediaBytes("video/mp4; codecs=\"avc1.640028\"", ByteArray(10) { it.toByte() })
            }
        }
    }
}
