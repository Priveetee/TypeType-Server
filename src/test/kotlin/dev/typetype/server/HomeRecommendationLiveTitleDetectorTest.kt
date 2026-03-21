package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationLiveTitleDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeRecommendationLiveTitleDetectorTest {

    @Test
    fun `detects live-like titles`() {
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("Channel is live now"))
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("DIRECT: football match"))
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("En direct ce soir"))
    }

    @Test
    fun `ignores normal titles`() {
        assertEquals(false, HomeRecommendationLiveTitleDetector.isLiveLike("Weekly tech roundup"))
        assertEquals(false, HomeRecommendationLiveTitleDetector.isLiveLike("How to build a server"))
    }
}
