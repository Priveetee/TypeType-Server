package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.ShortsVideoClassifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortsVideoClassifierTest {
    private fun video(url: String, duration: Long, shortFlag: Boolean, title: String = "v") = VideoItem(
        id = url,
        title = title,
        url = url,
        thumbnailUrl = "",
        uploaderName = "u",
        uploaderUrl = "ch",
        uploaderAvatarUrl = "",
        duration = duration,
        viewCount = 0,
        uploadDate = "",
        uploaded = duration,
        streamType = "video_stream",
        isShortFormContent = shortFlag,
        uploaderVerified = false,
        shortDescription = null,
    )

    @Test
    fun `select keeps strict shorts when present`() {
        val videos = listOf(
            video("https://yt.com/watch?v=long", duration = 600, shortFlag = false),
            video("https://yt.com/shorts/a", duration = 300, shortFlag = false),
            video("https://yt.com/watch?v=flag", duration = 600, shortFlag = true),
        )
        val selected = ShortsVideoClassifier.select(videos)
        assertEquals(2, selected.size)
        assertTrue(selected.any { it.url.contains("/shorts/") })
        assertTrue(selected.any { it.isShortFormContent })
    }

    @Test
    fun `select falls back to short-duration videos when strict yields empty`() {
        val videos = listOf(
            video("https://yt.com/watch?v=one", duration = 45, shortFlag = false),
            video("https://yt.com/watch?v=two", duration = 170, shortFlag = false),
            video("https://yt.com/watch?v=long", duration = 500, shortFlag = false),
        )
        val selected = ShortsVideoClassifier.select(videos)
        assertEquals(2, selected.size)
        assertTrue(selected.all { it.duration in 1..180 })
    }
}
