package dev.typetype.server

import dev.typetype.server.models.RecommendationEventItem
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val serializationJson: Json = Json { encodeDefaults = true }

class RecommendationEventItemSerializationTest {
    @Test
    fun `serialization includes publishedAt`() {
        val item = RecommendationEventItem(
            id = "id",
            eventType = "click",
            videoUrl = "https://yt.com/v/a",
            uploaderUrl = null,
            title = null,
            watchRatio = null,
            watchDurationMs = null,
            contextKey = null,
            occurredAt = 1_700_000_000_123L,
            publishedAt = 1_700_000_000_123L,
        )
        val json = serializationJson.encodeToString(RecommendationEventItem.serializer(), item)
        assertTrue(json.contains("\"publishedAt\":1700000000123"))
    }
}
