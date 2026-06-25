package dev.typetype.server.services

import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.models.HistoryItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

internal object HistoryProgressMapper {
    fun toHistoryItems(userId: String, rows: List<ResultRow>): List<HistoryItem> {
        val items = rows.map { it.toHistoryItem() }
        val savedProgress = savedProgressSeconds(userId, items.map { it.url })
        return items.map { YoutubeTypeTypeMapper.historyItem(it).withSavedProgress(savedProgress[it.url]) }
    }

    fun savedProgressSeconds(userId: String, videoUrl: String): Long? = savedProgressSeconds(userId, listOf(videoUrl))[videoUrl]

    fun savedProgressSeconds(userId: String, videoUrls: List<String>): Map<String, Long> {
        val urls = videoUrls.distinct()
        if (urls.isEmpty()) return emptyMap()
        return ProgressTable.selectAll()
            .where { (ProgressTable.userId eq userId) and (ProgressTable.videoUrl inList urls) }
            .associate { it[ProgressTable.videoUrl] to it[ProgressTable.position].toSeconds() }
    }

    private fun HistoryItem.withSavedProgress(savedProgress: Long?): HistoryItem =
        copy(progress = maxOf(progress, savedProgress ?: 0L))

    private fun Long.toSeconds(): Long = coerceAtLeast(0L) / 1_000L

    private fun ResultRow.toHistoryItem(): HistoryItem = HistoryItem(
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
