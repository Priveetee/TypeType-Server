package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.RecommendationEventsTable
import dev.typetype.server.models.RecommendationEventItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class RecommendationEventService(
    private val interestService: RecommendationInterestService,
    private val privacyService: RecommendationPrivacyService = RecommendationPrivacyService(SettingsService()),
) {
    suspend fun hasClick(userId: String): Boolean {
        if (!privacyService.isPersonalizationEnabled(userId)) return false
        return DatabaseFactory.query {
        RecommendationEventsTable.selectAll()
            .where { (RecommendationEventsTable.userId eq userId) and (RecommendationEventsTable.eventType eq "click") }
            .limit(1)
            .count() > 0
        }
    }

    suspend fun getAll(userId: String): List<RecommendationEventItem> {
        if (!privacyService.isPersonalizationEnabled(userId)) return emptyList()
        return DatabaseFactory.query {
        RecommendationEventsTable.selectAll()
            .where { RecommendationEventsTable.userId eq userId }
            .orderBy(RecommendationEventsTable.occurredAt to SortOrder.DESC)
            .limit(500)
            .map { it.toItem() }
        }
    }

    suspend fun add(
        userId: String,
        eventType: String,
        videoUrl: String?,
        uploaderUrl: String?,
        title: String?,
        watchRatio: Double?,
        watchDurationMs: Long?,
    ): RecommendationEventItem {
        if (!privacyService.isPersonalizationEnabled(userId)) {
            val now = System.currentTimeMillis()
            return RecommendationEventItem(
                id = "disabled",
                eventType = eventType,
                videoUrl = videoUrl,
                uploaderUrl = uploaderUrl,
                title = title,
                watchRatio = watchRatio,
                watchDurationMs = watchDurationMs,
                occurredAt = now,
            )
        }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            RecommendationEventsTable.insert {
                it[RecommendationEventsTable.id] = id
                it[RecommendationEventsTable.userId] = userId
                it[RecommendationEventsTable.eventType] = eventType
                it[RecommendationEventsTable.videoUrl] = videoUrl
                it[RecommendationEventsTable.uploaderUrl] = uploaderUrl
                it[RecommendationEventsTable.title] = title
                it[RecommendationEventsTable.watchRatio] = watchRatio
                it[RecommendationEventsTable.watchDurationMs] = watchDurationMs
                it[RecommendationEventsTable.occurredAt] = now
            }
        }
        interestService.update(userId, eventType, uploaderUrl, title, watchRatio)
        return RecommendationEventItem(id, eventType, videoUrl, uploaderUrl, title, watchRatio, watchDurationMs, now)
    }

    private fun ResultRow.toItem() = RecommendationEventItem(
        id = this[RecommendationEventsTable.id],
        eventType = this[RecommendationEventsTable.eventType],
        videoUrl = this[RecommendationEventsTable.videoUrl],
        uploaderUrl = this[RecommendationEventsTable.uploaderUrl],
        title = this[RecommendationEventsTable.title],
        watchRatio = this[RecommendationEventsTable.watchRatio],
        watchDurationMs = this[RecommendationEventsTable.watchDurationMs],
        occurredAt = this[RecommendationEventsTable.occurredAt],
    )
}
