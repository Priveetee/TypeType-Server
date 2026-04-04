package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.models.BlockedItem
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

private const val SCOPE_USER = "user"
private const val SCOPE_GLOBAL = "global"

class BlockedService(private val eventService: RecommendationEventService? = null) {
    suspend fun getChannels(userId: String): List<BlockedItem> = DatabaseFactory.query {
        BlockedChannelsTable.selectAll()
            .where { (BlockedChannelsTable.userId eq userId) or (BlockedChannelsTable.scope eq SCOPE_GLOBAL) }
            .orderBy(BlockedChannelsTable.blockedAt to SortOrder.DESC)
            .map { row ->
                BlockedItem(
                    url = row[BlockedChannelsTable.channelUrl],
                    name = row[BlockedChannelsTable.channelName],
                    thumbnailUrl = row[BlockedChannelsTable.channelThumbnailUrl],
                    blockedAt = row[BlockedChannelsTable.blockedAt],
                )
            }
    }

    suspend fun getVideos(userId: String): List<BlockedItem> = DatabaseFactory.query {
        BlockedVideosTable.selectAll()
            .where { (BlockedVideosTable.userId eq userId) or (BlockedVideosTable.scope eq SCOPE_GLOBAL) }
            .orderBy(BlockedVideosTable.blockedAt to SortOrder.DESC)
            .map { row -> BlockedItem(url = row[BlockedVideosTable.videoUrl], blockedAt = row[BlockedVideosTable.blockedAt]) }
    }

    suspend fun addChannel(
        userId: String,
        url: String,
        name: String? = null,
        thumbnailUrl: String? = null,
        global: Boolean = false,
    ): BlockedItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            BlockedChannelsTable.insert {
                it[BlockedChannelsTable.userId] = userId
                it[BlockedChannelsTable.scope] = if (global) SCOPE_GLOBAL else SCOPE_USER
                it[channelUrl] = url
                it[channelName] = name
                it[channelThumbnailUrl] = thumbnailUrl
                it[blockedAt] = now
            }
        }
        eventService?.add(userId, "block_channel", null, url, name, null, null)
        return BlockedItem(url = url, name = name, thumbnailUrl = thumbnailUrl, blockedAt = now)
    }

    suspend fun addVideo(userId: String, url: String, global: Boolean = false): BlockedItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            BlockedVideosTable.insert {
                it[BlockedVideosTable.userId] = userId
                it[BlockedVideosTable.scope] = if (global) SCOPE_GLOBAL else SCOPE_USER
                it[videoUrl] = url
                it[blockedAt] = now
            }
        }
        eventService?.add(userId, "block_video", url, null, null, null, null)
        return BlockedItem(url = url, blockedAt = now)
    }

    suspend fun deleteChannel(userId: String, url: String, role: String): Boolean = DatabaseFactory.query {
        val canDeleteGlobal = role == "admin" || role == "moderator"
        val scopeClause = if (canDeleteGlobal) {
            (BlockedChannelsTable.scope eq SCOPE_GLOBAL) or (BlockedChannelsTable.userId eq userId)
        } else {
            (BlockedChannelsTable.scope eq SCOPE_USER) and (BlockedChannelsTable.userId eq userId)
        }
        BlockedChannelsTable.deleteWhere { (BlockedChannelsTable.channelUrl eq url) and scopeClause } > 0
    }

    suspend fun deleteVideo(userId: String, url: String, role: String): Boolean = DatabaseFactory.query {
        val canDeleteGlobal = role == "admin" || role == "moderator"
        val scopeClause = if (canDeleteGlobal) {
            (BlockedVideosTable.scope eq SCOPE_GLOBAL) or (BlockedVideosTable.userId eq userId)
        } else {
            (BlockedVideosTable.scope eq SCOPE_USER) and (BlockedVideosTable.userId eq userId)
        }
        BlockedVideosTable.deleteWhere { (BlockedVideosTable.videoUrl eq url) and scopeClause } > 0
    }
}
