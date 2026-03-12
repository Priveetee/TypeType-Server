package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.models.HistoryItem
import org.jetbrains.exposed.v1.core.LowerCase
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class HistoryService {

    suspend fun search(q: String?, from: Long?, to: Long?, limit: Int, offset: Int): Pair<List<HistoryItem>, Long> = DatabaseFactory.query {
        val query = HistoryTable.selectAll()
        if (!q.isNullOrBlank()) {
            val pattern = "%${q.lowercase()}%"
            query.andWhere { (LowerCase(HistoryTable.title) like pattern) or (LowerCase(HistoryTable.channelName) like pattern) }
        }
        if (from != null) query.andWhere { HistoryTable.watchedAt greaterEq from }
        if (to != null) query.andWhere { HistoryTable.watchedAt less to }
        val total = query.count()
        val items = query.orderBy(HistoryTable.watchedAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toHistoryItem() }
        items to total
    }

    suspend fun add(item: HistoryItem): HistoryItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            HistoryTable.insert {
                it[HistoryTable.id] = id
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

    suspend fun delete(id: String): Boolean = DatabaseFactory.query {
        HistoryTable.deleteWhere { HistoryTable.id eq id } > 0
    }

    suspend fun deleteAll(): Unit = DatabaseFactory.query {
        HistoryTable.deleteAll()
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
