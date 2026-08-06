package dev.typetype.server.services

import dev.typetype.server.models.BlockedItem
import dev.typetype.server.models.BlockedKeywordItem
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.VideoItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockedHomeRecommendationsFilterTest {
    @Test
    fun `filters cached recommendations with current blocked profile`() {
        val response = HomeRecommendationsResponse(
            items = listOf(
                video("blocked-video", "Keep", "Allowed", "channel:allowed"),
                video("allowed", "Keep", "Blocked", "channel:blocked"),
                video("keyword", "Hide this topic", "Allowed", "channel:allowed"),
                video("visible", "Keep", "Allowed", "channel:allowed"),
            ),
            nextCursor = null,
            hasMore = false,
        )
        val profile = BlockedContentProfile(
            videos = listOf(BlockedItem(url = "https://youtube.com/watch?v=blocked-video")),
            channels = listOf(BlockedItem(url = "channel:blocked", name = "Blocked")),
            keywords = listOf(BlockedKeywordItem(keyword = "hide this")),
        )

        assertEquals(listOf("visible"), response.filterBlocked(profile).items.map { it.id })
    }

    private fun video(id: String, title: String, uploaderName: String, uploaderUrl: String) = VideoItem(
        id = id,
        title = title,
        url = "https://youtube.com/watch?v=$id",
        thumbnailUrl = "",
        uploaderName = uploaderName,
        uploaderUrl = uploaderUrl,
        uploaderAvatarUrl = "",
        duration = 60,
        viewCount = 0,
        uploadDate = "",
        uploaded = 0,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
}
