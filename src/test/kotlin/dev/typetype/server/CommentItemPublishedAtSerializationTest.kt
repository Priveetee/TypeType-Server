package dev.typetype.server

import dev.typetype.server.models.CommentItem
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val serializationJson: Json = Json { encodeDefaults = true }

class CommentItemPublishedAtSerializationTest {
    @Test
    fun `serialization includes nullable publishedAt`() {
        val item = CommentItem(
            id = "c",
            text = "text",
            author = "a",
            authorUrl = "",
            authorAvatarUrl = "",
            likeCount = 0,
            textualLikeCount = "",
            publishedTime = "2 days ago",
            publishedAt = 1_700_000_000_123L,
            isHeartedByUploader = false,
            isPinned = false,
            uploaderVerified = false,
            replyCount = 0,
            repliesPage = null,
        )
        val json = serializationJson.encodeToString(CommentItem.serializer(), item)
        assertTrue(json.contains("\"publishedAt\":1700000000123"))
    }
}
