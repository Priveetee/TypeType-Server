package dev.typetype.server

import dev.typetype.server.services.validateProxyUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UrlValidatorTest {
    @Test
    fun `rejects malformed and unsupported urls`() {
        assertEquals("Malformed URL", validateProxyUrl("not a url"))
        assertEquals("Missing URL scheme", validateProxyUrl("example.com/video"))
        assertEquals("Unsupported URL scheme: ftp", validateProxyUrl("ftp://example.com/video"))
        assertEquals("Missing URL host", validateProxyUrl("https:///video"))
    }

    @Test
    fun `blocks localhost and private ipv4 ranges`() {
        assertEquals("Blocked host", validateProxyUrl("http://localhost/video"))
        assertEquals("Blocked host", validateProxyUrl("https://demo.localhost/video"))
        assertEquals("Blocked private address", validateProxyUrl("http://127.0.0.1/video"))
        assertEquals("Blocked private address", validateProxyUrl("http://10.0.0.1/video"))
        assertEquals("Blocked private address", validateProxyUrl("http://172.16.0.1/video"))
        assertEquals("Blocked private address", validateProxyUrl("http://192.168.1.1/video"))
    }

    @Test
    fun `allows public and ipv6 addresses`() {
        assertNull(validateProxyUrl("https://1.1.1.1/videoplayback?id=1"))
        assertNull(validateProxyUrl("https://[2606:4700:4700::1111]/videoplayback?id=1"))
    }
}
