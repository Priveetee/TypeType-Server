package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.toShortCanonicalUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShortCanonicalUrlTest {
    private fun item(url: String) = VideoItem(
        id = "id", title = "t", url = url, thumbnailUrl = "", uploaderName = "u", uploaderUrl = "ch",
        uploaderAvatarUrl = "", duration = 10, viewCount = 0, uploadDate = "", uploaded = 1,
        streamType = "video_stream", isShortFormContent = true, uploaderVerified = false, shortDescription = null,
    )

    @Test
    fun `converts youtube watch url to shorts canonical url`() {
        val converted = item("https://www.youtube.com/watch?v=GKpwO-DGKiI&feature=share").toShortCanonicalUrl()
        assertEquals("https://www.youtube.com/shorts/GKpwO-DGKiI", converted.url)
    }

    @Test
    fun `keeps already canonical shorts url`() {
        val converted = item("https://www.youtube.com/shorts/GKpwO-DGKiI").toShortCanonicalUrl()
        assertEquals("https://www.youtube.com/shorts/GKpwO-DGKiI", converted.url)
    }
}
