package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationThemeExtractor
import dev.typetype.server.services.RecommendationTopicTokenizer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationTokenNormalizationTest {
    @Test
    fun `topic tokenizer keeps normalized french tokens`() {
        val tokens = RecommendationTopicTokenizer.tokenize("L'ÉPOQUE OÙ ANGELO LA DÉBROUILLE")
        assertTrue("epoque" in tokens)
        assertTrue("debrouille" in tokens)
    }

    @Test
    fun `theme extractor filters generic investigation noise`() {
        val score = HomeRecommendationThemeExtractor.computeThemeScore(
            videoTitle = "Murder investigation full episode recap",
            channelName = "Crime News",
            themeTokens = setOf("linux", "gaming", "gta"),
        )
        assertTrue(score <= 0.1)
    }
}
