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
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class HistoryService {

    suspend fun search(userId: String, q: String?, from: Long?, to: Long?, limit: Int, offset: Int): Pair<List<HistoryItem>, Long> = DatabaseFactory.query {
        val query = HistoryTable.selectAll().where { HistoryTable.userId eq userId }
        if (!q.isNullOrBlank()) {
            val pattern = "%${q.lowercase()}%"
            query.andWhere { (LowerCase(HistoryTable.title) like pattern) or (LowerCase(HistoryTable.channelName) like pattern) }
        }
        if (from != null) query.andWhere { HistoryTable.watchedAt greaterEq from }
        if (to != null) query.andWhere { HistoryTable.watchedAt less to }
        val total = query.count()
        val items = query.orderBy(HistoryTable.watchedAt to SortOrder.DESC, HistoryTable.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toHistoryItem() }
        items to total
    }

    suspend fun add(userId: String, item: HistoryItem): HistoryItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
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
                it[watchedAt] = now
            }
        }
        return item.copy(id = id, watchedAt = now)
    }

    suspend fun delete(userId: String, id: String): Boolean = DatabaseFactory.query {
        HistoryTable.deleteWhere { HistoryTable.id eq id and (HistoryTable.userId eq userId) } > 0
    }

    suspend fun deleteAll(userId: String): Unit = DatabaseFactory.query {
        HistoryTable.deleteWhere { HistoryTable.userId eq userId }
    }

    private fun ResultRow.toHistoryItem() = HistoryItem(
        id = this[HistoryTable.id],
        url = this[HistoryTable.url],
        title = this[HistoryTable.title],
        thumbnail = this[HistoryTable.thumbnail],
        channelName = this[HistoryTable.channelName],
        channelUrl = this[HistoryTable.channelUrl],
        channelAvatar = this[HistoryTable.channelAvatar],
        duration = this[HistoryTable.duration],
        progress = this[HistoryTable.progress],
        watchedAt = this[HistoryTable.watchedAt],
    )
}
