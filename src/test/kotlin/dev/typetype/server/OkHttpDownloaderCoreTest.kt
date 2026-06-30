package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.downloader.OkHttpDownloader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OkHttpDownloaderCoreTest {
    @Test
    fun `execute maps http response payload`() {
        val server = server(status = 200, body = "ok")
        server.start()
        try {
            val request = ExtractorRequest.newBuilder().get(urlOf(server, "/ok")).build()
            val response = OkHttpDownloader.instance().execute(request)
            assertEquals(200, response.responseCode())
            assertEquals("ok", response.responseBody())
            assertTrue(response.latestUrl().contains("/ok"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `execute throws recaptcha on 429`() {
        val server = server(status = 429, body = "blocked")
        server.start()
        try {
            val request = ExtractorRequest.newBuilder().get(urlOf(server, "/blocked")).build()
            var thrown: Throwable? = null
            runCatching { OkHttpDownloader.instance().execute(request) }.onFailure { thrown = it }
            assertEquals(ReCaptchaException::class.java, thrown?.javaClass)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `executeAsync invokes success callback and finishes call`() {
        val server = server(status = 200, body = "async")
        server.start()
        try {
            val request = ExtractorRequest.newBuilder().get(urlOf(server, "/async")).build()
            val latch = CountDownLatch(1)
            var status: Int? = null
            var error: Exception? = null
            val call = OkHttpDownloader.instance().executeAsync(request, object : Downloader.AsyncCallback {
                override fun onSuccess(response: Response) {
                    status = response.responseCode()
                    latch.countDown()
                }

                override fun onError(e: Exception) {
                    error = e
                    latch.countDown()
                }
            })
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals(200, status)
            assertNull(error)
            assertTrue(waitUntilFinished(call))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `postStreaming streams post response and keeps request headers`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val receivedBody = AtomicReference("")
        val receivedHeader = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        server.createContext("/stream") { exchange ->
            receivedBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            receivedHeader.set(exchange.requestHeaders.getFirst("X-Test"))
            exchange.responseHeaders.add("Content-Type", "application/vnd.yt-ump")
            val bytes = "stream-ok".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            latch.countDown()
        }
        server.start()
        try {
            OkHttpDownloader.instance().postStreaming(
                urlOf(server, "/stream"),
                mutableMapOf("X-Test" to mutableListOf("sabr")),
                "payload".toByteArray(),
                null,
            ).use { response ->
                assertEquals(200, response.responseCode())
                assertEquals("application/vnd.yt-ump", response.getHeader("Content-Type"))
                assertEquals("stream-ok", response.body().readBytes().toString(Charsets.UTF_8))
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals("payload", receivedBody.get())
            assertEquals("sabr", receivedHeader.get())
        } finally {
            server.stop(0)
        }
    }

    private fun waitUntilFinished(call: org.schabi.newpipe.extractor.downloader.CancellableCall): Boolean {
        repeat(50) {
            if (call.isFinished()) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun urlOf(server: HttpServer, path: String): String =
        "http://127.0.0.1:${server.address.port}$path"

    private fun server(status: Int, body: String): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        return server
    }
}
