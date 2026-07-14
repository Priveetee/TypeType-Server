package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeThumbnailProxyFallbackTest {
    @Test
    fun `accepts ytimg jpeg body returned with 404`() {
        assertTrue(acceptsYoutubeThumbnailFallback("https://i.ytimg.com/vi/id/hqdefault.jpg", 404, "image/jpeg"))
    }

    @Test
    fun `rejects non image and unrelated 404 responses`() {
        assertFalse(acceptsYoutubeThumbnailFallback("https://i.ytimg.com/vi/id/hqdefault.jpg", 404, "text/html"))
        assertFalse(acceptsYoutubeThumbnailFallback("https://example.com/image.jpg", 404, "image/jpeg"))
        assertFalse(acceptsYoutubeThumbnailFallback("https://i.ytimg.com/vi/id/hqdefault.jpg", 403, "image/jpeg"))
    }
}
