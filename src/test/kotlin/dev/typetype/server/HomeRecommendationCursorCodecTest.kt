package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationCursorCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeRecommendationCursorCodecTest {

    @Test
    fun `decode null cursor defaults to zero`() {
        val cursor = HomeRecommendationCursorCodec.decode(null)
        assertEquals(0, cursor?.index)
    }

    @Test
    fun `encode decode roundtrip preserves index`() {
        val encoded = HomeRecommendationCursorCodec.encode(HomeRecommendationCursor(index = 40))
        val decoded = HomeRecommendationCursorCodec.decode(encoded)
        assertEquals(40, decoded?.index)
    }

    @Test
    fun `decode invalid cursor returns null`() {
        assertNull(HomeRecommendationCursorCodec.decode("not_base64"))
    }
}
