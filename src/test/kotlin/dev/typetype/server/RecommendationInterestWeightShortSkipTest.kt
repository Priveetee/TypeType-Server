package dev.typetype.server

import dev.typetype.server.services.RecommendationInterestWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecommendationInterestWeightShortSkipTest {
    @Test
    fun `short skip maps to negative interest weight`() {
        assertEquals(-1.0, RecommendationInterestWeight.of("short_skip", null))
    }
}
