package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class DownloaderGatewayRequestIdTest {
    @Test
    fun `downloader gateway forwards request id`() = testApplication {
        val forwardedRequestId = AtomicReference<String>()
        val upstream = HttpServer.create(InetSocketAddress(0), 0)
        upstream.createContext("/health") { exchange ->
            forwardedRequestId.set(exchange.requestHeaders.getFirst(REQUEST_ID_HEADER))
            val payload = "{}".toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        upstream.start()

        val gateway = DownloaderGatewayService(baseUrl = "http://127.0.0.1:${upstream.address.port}")
        application {
            installRequestObservability()
            routing { downloaderGatewayRoutes(gateway) }
        }

        try {
            val response = client.get("/downloader/health") { header(REQUEST_ID_HEADER, "request-test-123") }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("request-test-123", forwardedRequestId.get())
        } finally {
            upstream.stop(0)
        }
    }
}
