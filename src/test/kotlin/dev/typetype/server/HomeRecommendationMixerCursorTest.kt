package dev.typetype.server

import dev.typetype.server.HomeRecommendationItemFixtures.context
import dev.typetype.server.HomeRecommendationItemFixtures.video
import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationCursorCodec
import dev.typetype.server.services.HomeRecommendationMixer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationMixerCursorTest {
    @Test
    fun `mix carries recent channels in cursor payload`() {
        val pool = HomeRecommendationPool(
            subscriptions = listOf(video("s1", "a"), video("s2", "b"), video("s3", "c")),
            discovery = listOf(video("d1", "d"), video("d2", "e"), video("d3", "f")),
        )
        val page = HomeRecommendationMixer.mix(pool = pool, cursor = HomeRecommendationCursor(), limit = 4, context = context)
        val decoded = HomeRecommendationCursorCodec.decode(page.nextCursor)
        assertTrue((decoded?.recentChannels?.size ?: 0) > 0)
    }

    @Test
    fun `mix carries semantic keys in cursor payload`() {
        val pool = HomeRecommendationPool(
            subscriptions = listOf(
                video("s1", "a").copy(title = "linux kernel update"),
                video("s2", "b").copy(title = "android privacy guide"),
                video("s3", "c").copy(title = "music discovery mix"),
            ),
            discovery = listOf(
                video("d1", "d").copy(title = "gaming highlights daily"),
                video("d2", "e").copy(title = "science world news"),
                video("d3", "f").copy(title = "coding tutorial kotlin"),
            ),
        )
        val page = HomeRecommendationMixer.mix(pool = pool, cursor = HomeRecommendationCursor(), limit = 4, context = context)
        val decoded = HomeRecommendationCursorCodec.decode(page.nextCursor)
        assertTrue((decoded?.recentSemanticKeys?.size ?: 0) > 0)
    }
}
