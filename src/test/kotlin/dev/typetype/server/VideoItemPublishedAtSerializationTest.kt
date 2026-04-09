package dev.typetype.server

import dev.typetype.server.models.VideoItem
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val serializationJson: Json = Json { encodeDefaults = true }

class VideoItemPublishedAtSerializationTest {
    @Test
    fun `serialization includes publishedAt when present`() {
        val item = VideoItem(
            id = "id",
            title = "title",
            url = "https://yt.com/watch?v=id",
            thumbnailUrl = "",
            uploaderName = "u",
            uploaderUrl = "",
            uploaderAvatarUrl = "",
            duration = 1,
            viewCount = 2,
            uploadDate = "",
            uploaded = 1_700_000_000_123L,
            streamType = "video",
            isShortFormContent = false,
            uploaderVerified = false,
            shortDescription = null,
            publishedAt = 1_700_000_000_123L,
        )
        val json = serializationJson.encodeToString(VideoItem.serializer(), item)
        assertTrue(json.contains("\"publishedAt\":1700000000123"))
    }
}
