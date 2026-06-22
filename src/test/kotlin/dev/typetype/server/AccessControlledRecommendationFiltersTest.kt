package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.AllowedChannelItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.AccessControlProfile
import dev.typetype.server.services.filterAllowed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccessControlledRecommendationFiltersTest {
    @Test
    fun `home recommendation responses keep only allowed channels`() {
        val response = HomeRecommendationsResponse(
            items = listOf(video("Allowed", "https://youtube.com/@allowed"), video("Blocked", "https://youtube.com/@blocked")),
            nextCursor = null,
            hasMore = false,
        )
        val profile = AccessControlProfile(
            enabled = true,
            channels = listOf(AllowedChannelItem(url = "https://youtube.com/@allowed", name = "Allowed")),
        )

        val filtered = response.filterAllowed(profile)

        assertEquals(listOf("Allowed"), filtered.items.map { it.title })
    }

    private fun video(name: String, url: String): VideoItem =
        testVideoItem().copy(title = name, uploaderName = name, uploaderUrl = url)
}
