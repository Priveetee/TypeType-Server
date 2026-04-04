package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationCursorCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeRecommendationCursorCodecTest {

    @Test
    fun `decode null cursor defaults to zero indexes`() {
        val cursor = HomeRecommendationCursorCodec.decode(null)
        assertEquals(0, cursor?.subscriptionIndex)
        assertEquals(0, cursor?.discoveryIndex)
    }

    @Test
    fun `encode decode roundtrip preserves indexes`() {
        val encoded = HomeRecommendationCursorCodec.encode(
            HomeRecommendationCursor(
                subscriptionIndex = 40,
                discoveryIndex = 12,
                subscriptionRun = 2,
                preferDiscovery = false,
                recentChannels = listOf("c1", "c2"),
                recentSemanticKeys = listOf("linux|kernel"),
            )
        )
        val decoded = HomeRecommendationCursorCodec.decode(encoded)
        assertEquals(40, decoded?.subscriptionIndex)
        assertEquals(12, decoded?.discoveryIndex)
        assertEquals(2, decoded?.subscriptionRun)
        assertEquals(false, decoded?.preferDiscovery)
        assertEquals(listOf("c1", "c2"), decoded?.recentChannels)
        assertEquals(listOf("linux|kernel"), decoded?.recentSemanticKeys)
    }

    @Test
    fun `decode legacy index cursor maps to both indexes`() {
        val legacy = "eyJpbmRleCI6NX0"
        val decoded = HomeRecommendationCursorCodec.decode(legacy)
        assertEquals(5, decoded?.subscriptionIndex)
        assertEquals(5, decoded?.discoveryIndex)
    }

    @Test
    fun `decode invalid cursor returns null`() {
        assertNull(HomeRecommendationCursorCodec.decode("not_base64"))
    }
}
