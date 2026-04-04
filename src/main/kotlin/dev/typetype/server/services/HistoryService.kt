package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.models.HistoryItem
import org.jetbrains.exposed.v1.core.LowerCase
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class HistoryService(
    private val eventService: RecommendationEventService? = null,
    private val privacyService: RecommendationPrivacyService = RecommendationPrivacyService(SettingsService()),
) {
    suspend fun search(userId: String, q: String?, from: Long?, to: Long?, limit: Int, offset: Int): Pair<List<HistoryItem>, Long> = DatabaseFactory.query {
        val query = HistoryTable.selectAll().where { HistoryTable.userId eq userId }
        if (!q.isNullOrBlank()) {
            val pattern = "%${q.lowercase()}%"
            query.andWhere { (LowerCase(HistoryTable.title) like pattern) or (LowerCase(HistoryTable.channelName) like pattern) }
        }
        if (from != null) query.andWhere { HistoryTable.watchedAt greaterEq from }
        if (to != null) query.andWhere { HistoryTable.watchedAt less to }
        val total = query.count()
        val items = query.orderBy(HistoryTable.watchedAt to SortOrder.DESC, HistoryTable.id to SortOrder.DESC).limit(limit).offset(offset.toLong()).map { it.toHistoryItem() }
        items to total
    }

    suspend fun add(userId: String, item: HistoryItem): HistoryItem = insert(userId, item, System.currentTimeMillis())

    suspend fun addImported(userId: String, item: HistoryItem): HistoryItem = insert(userId, item, item.watchedAt.takeIf { it > 0 } ?: System.currentTimeMillis())

    suspend fun addImportedBatch(userId: String, items: List<HistoryItem>): Int {
        if (items.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val rows = items.map { item ->
            val watchedAt = item.watchedAt.takeIf { it > 0 } ?: now
            Triple(UUID.randomUUID().toString(), item, watchedAt)
        }
        DatabaseFactory.query {
            HistoryTable.batchInsert(data = rows, shouldReturnGeneratedValues = false) { (id, item, watchedAt) ->
                this[HistoryTable.id] = id
                this[HistoryTable.userId] = userId
                this[HistoryTable.url] = item.url
                this[HistoryTable.title] = item.title
                this[HistoryTable.thumbnail] = item.thumbnail
                this[HistoryTable.channelName] = item.channelName
                this[HistoryTable.channelUrl] = item.channelUrl
                this[HistoryTable.channelAvatar] = item.channelAvatar
                this[HistoryTable.duration] = item.duration
                this[HistoryTable.progress] = item.progress
                this[HistoryTable.watchedAt] = watchedAt
            }
        }
        return rows.size
    }

    suspend fun dedupKeys(userId: String): Set<Pair<String, Long>> = DatabaseFactory.query { HistoryTable.selectAll().where { HistoryTable.userId eq userId }.map { it[HistoryTable.url] to it[HistoryTable.watchedAt] }.toSet() }

    suspend fun delete(userId: String, id: String): Boolean = DatabaseFactory.query { HistoryTable.deleteWhere { HistoryTable.id eq id and (HistoryTable.userId eq userId) } > 0 }

    suspend fun deleteAll(userId: String): Unit = DatabaseFactory.query { HistoryTable.deleteWhere { HistoryTable.userId eq userId } }

    private suspend fun insert(userId: String, item: HistoryItem, watchedAt: Long): HistoryItem {
        val id = UUID.randomUUID().toString()
        DatabaseFactory.query {
            HistoryTable.insert {
                it[HistoryTable.id] = id
                it[HistoryTable.userId] = userId
                it[url] = item.url
                it[title] = item.title
                it[thumbnail] = item.thumbnail
                it[channelName] = item.channelName
                it[channelUrl] = item.channelUrl
                it[channelAvatar] = item.channelAvatar
                it[duration] = item.duration
                it[progress] = item.progress
                it[HistoryTable.watchedAt] = watchedAt
            }
        }
        val ratio = if (item.duration > 0) item.progress.toDouble() / item.duration.toDouble() else 0.0
        if (privacyService.isPersonalizationEnabled(userId)) {
            eventService?.add(
                userId = userId,
                eventType = "watch",
                videoUrl = item.url,
                uploaderUrl = item.channelUrl,
                title = item.title,
                watchRatio = ratio.coerceIn(0.0, 1.0),
                watchDurationMs = item.progress * 1_000L,
            )
        }
        return item.copy(id = id, watchedAt = watchedAt)
    }

    private fun ResultRow.toHistoryItem() = HistoryItem(id = this[HistoryTable.id], url = this[HistoryTable.url], title = this[HistoryTable.title], thumbnail = this[HistoryTable.thumbnail], channelName = this[HistoryTable.channelName], channelUrl = this[HistoryTable.channelUrl], channelAvatar = this[HistoryTable.channelAvatar], duration = this[HistoryTable.duration], progress = this[HistoryTable.progress], watchedAt = this[HistoryTable.watchedAt])
}
