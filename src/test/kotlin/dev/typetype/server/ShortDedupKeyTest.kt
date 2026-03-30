package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.toShortDedupKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShortDedupKeyTest {
    private fun item(id: String, url: String) = VideoItem(
        id = id,
        title = "t",
        url = url,
        thumbnailUrl = "",
        uploaderName = "u",
        uploaderUrl = "ch",
        uploaderAvatarUrl = "",
        duration = 10,
        viewCount = 0,
        uploadDate = "",
        uploaded = -1,
        streamType = "video_stream",
        isShortFormContent = true,
        uploaderVerified = false,
        shortDescription = null,
    )

    @Test
    fun `same video id shares same dedup key across watch and shorts urls`() {
        val watch = item("a", "https://www.youtube.com/watch?v=GKpwO-DGKiI")
        val shorts = item("b", "https://www.youtube.com/shorts/GKpwO-DGKiI")
        assertEquals("GKpwO-DGKiI", watch.toShortDedupKey())
        assertEquals("GKpwO-DGKiI", shorts.toShortDedupKey())
    }
}
