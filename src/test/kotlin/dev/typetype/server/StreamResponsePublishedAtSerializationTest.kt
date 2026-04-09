package dev.typetype.server

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val serializationJson: Json = Json { encodeDefaults = true }

class StreamResponsePublishedAtSerializationTest {
    @Test
    fun `serialization includes publishedAt when present`() {
        val json = serializationJson.encodeToString(
            dev.typetype.server.models.StreamResponse.serializer(),
            testStreamResponse().copy(publishedAt = 1_700_000_000_123L),
        )
        assertTrue(json.contains("\"publishedAt\":1700000000123"))
    }
}
