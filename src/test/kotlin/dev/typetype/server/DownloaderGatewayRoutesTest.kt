package dev.typetype.server

import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloaderGatewayRoutesTest {
    @Test
    fun `sse downloader route proxies text event stream response`() = testApplication {
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs/test/events") { exchange ->
            val payload = "data: {\"status\":\"running\"}\n\n".toByteArray()
            exchange.responseHeaders.add(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
            exchange.responseHeaders.add(HttpHeaders.CacheControl, "no-cache")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        upstream.start()

        val port = upstream.address.port
        val gateway = DownloaderGatewayService("http://127.0.0.1:$port")

        application {
            routing {
                downloaderGatewayRoutes(gateway)
            }
        }

        try {
            val response = client.get("/downloader/jobs/test/events") {
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().contains("text/event-stream"))
            assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
        } finally {
            upstream.stop(0)
        }
    }

    @Test
    fun `artifact route forces attachment headers for ios safari`() = testApplication {
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/jobs/test/artifact") { exchange ->
            val payload = "abc".toByteArray()
            exchange.responseHeaders.add(HttpHeaders.ContentType, "video/mp4")
            exchange.responseHeaders.add(HttpHeaders.ContentDisposition, "inline; filename=\"demo.mp4\"")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        upstream.start()

        val port = upstream.address.port
        val gateway = DownloaderGatewayService("http://127.0.0.1:$port")

        application {
            routing {
                downloaderGatewayRoutes(gateway)
            }
        }

        try {
            val response = client.get("/downloader/jobs/test/artifact")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().contains("application/octet-stream"))
            assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment"))
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
            assertEquals("abc", response.bodyAsText())
        } finally {
            upstream.stop(0)
        }
    }
}
