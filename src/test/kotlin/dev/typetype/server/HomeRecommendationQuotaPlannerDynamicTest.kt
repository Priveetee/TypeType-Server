package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationQuotaPlanner
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.HomeRecommendationSourceTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationQuotaPlannerDynamicTest {
    @Test
    fun `planner raises discovery target when discovery sources dominate`() {
        val sourceMap = buildMap {
            repeat(20) { put("d$it", HomeRecommendationSourceTag.DISCOVERY_THEME) }
            repeat(3) { put("s$it", HomeRecommendationSourceTag.SUBSCRIPTION) }
        }
        val planner = HomeRecommendationQuotaPlanner(
            limit = 10,
            subscriptionSize = 10,
            discoverySize = 25,
            sourceByUrl = sourceMap,
            sessionContext = context,
        )
        assertTrue(planner.target.targetDiscovery >= 6)
    }

    @Test
    fun `planner keeps balanced target when no discovery source advantage`() {
        val sourceMap = buildMap {
            repeat(8) { put("s$it", HomeRecommendationSourceTag.SUBSCRIPTION) }
            repeat(6) { put("d$it", HomeRecommendationSourceTag.DISCOVERY_TRENDING) }
        }
        val planner = HomeRecommendationQuotaPlanner(
            limit = 10,
            subscriptionSize = 10,
            discoverySize = 10,
            sourceByUrl = sourceMap,
            sessionContext = context,
        )
        assertEquals(5, planner.target.targetDiscovery)
    }

    private val context = HomeRecommendationSessionContext(
        intent = HomeRecommendationSessionIntent.AUTO,
        deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
    )
}
