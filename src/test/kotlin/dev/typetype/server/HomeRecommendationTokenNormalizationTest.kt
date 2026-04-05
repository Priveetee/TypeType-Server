package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationTokenNormalizer
import dev.typetype.server.services.RecommendationTopicTokenizer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationTokenNormalizationTest {
    @Test
    fun `topic tokenizer keeps normalized latin tokens`() {
        val tokens = RecommendationTopicTokenizer.tokenize("ePOQUE DEBROUILLE")
        assertTrue("epoque" in tokens)
        assertTrue("debrouille" in tokens)
    }

    @Test
    fun `token normalizer strips punctuation and case`() {
        val normalized = HomeRecommendationTokenNormalizer.normalize("Topic-42!")
        assertTrue(normalized == "topic42")
    }
}
