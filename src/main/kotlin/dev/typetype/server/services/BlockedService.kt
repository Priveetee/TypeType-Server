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

class BlockedService {

    suspend fun getChannels(userId: String): List<BlockedItem> = DatabaseFactory.query {
        BlockedChannelsTable.selectAll()
            .where { (BlockedChannelsTable.userId eq userId) or (BlockedChannelsTable.scope eq SCOPE_GLOBAL) }
            .orderBy(BlockedChannelsTable.blockedAt to SortOrder.DESC)
            .map {
                BlockedItem(
                    url = it[BlockedChannelsTable.channelUrl],
                    name = it[BlockedChannelsTable.channelName],
                    thumbnailUrl = it[BlockedChannelsTable.channelThumbnailUrl],
                    blockedAt = it[BlockedChannelsTable.blockedAt],
                )
            }
    }

    suspend fun addChannel(userId: String, url: String, name: String? = null, thumbnailUrl: String? = null, global: Boolean = false): BlockedItem {
        val now = System.currentTimeMillis()
        val scope = if (global) SCOPE_GLOBAL else SCOPE_USER
        DatabaseFactory.query {
            BlockedChannelsTable.insert {
                it[BlockedChannelsTable.userId] = userId
                it[BlockedChannelsTable.scope] = scope
                it[channelUrl] = url
                it[channelName] = name
                it[channelThumbnailUrl] = thumbnailUrl
                it[blockedAt] = now
            }
        }
        return BlockedItem(url = url, name = name, thumbnailUrl = thumbnailUrl, blockedAt = now)
    }

    suspend fun deleteChannel(userId: String, url: String, role: String): Boolean = DatabaseFactory.query {
        val canDeleteGlobal = role == "admin" || role == "moderator"
        BlockedChannelsTable.deleteWhere {
            (BlockedChannelsTable.channelUrl eq url) and
                ((BlockedChannelsTable.userId eq userId) or (if (canDeleteGlobal) (BlockedChannelsTable.scope eq SCOPE_GLOBAL) else (BlockedChannelsTable.scope eq "")))
        } > 0
    }

    suspend fun getVideos(userId: String): List<BlockedItem> = DatabaseFactory.query {
        BlockedVideosTable.selectAll()
            .where { (BlockedVideosTable.userId eq userId) or (BlockedVideosTable.scope eq SCOPE_GLOBAL) }
            .orderBy(BlockedVideosTable.blockedAt to SortOrder.DESC)
            .map { BlockedItem(url = it[BlockedVideosTable.videoUrl], blockedAt = it[BlockedVideosTable.blockedAt]) }
    }

    suspend fun addVideo(userId: String, url: String, global: Boolean = false): BlockedItem {
        val now = System.currentTimeMillis()
        val scope = if (global) SCOPE_GLOBAL else SCOPE_USER
        DatabaseFactory.query {
            BlockedVideosTable.insert {
                it[BlockedVideosTable.userId] = userId
                it[BlockedVideosTable.scope] = scope
                it[videoUrl] = url
                it[blockedAt] = now
            }
        }
        return BlockedItem(url = url, blockedAt = now)
    }

    suspend fun deleteVideo(userId: String, url: String, role: String): Boolean = DatabaseFactory.query {
        val canDeleteGlobal = role == "admin" || role == "moderator"
        BlockedVideosTable.deleteWhere {
            (BlockedVideosTable.videoUrl eq url) and
                ((BlockedVideosTable.userId eq userId) or (if (canDeleteGlobal) (BlockedVideosTable.scope eq SCOPE_GLOBAL) else (BlockedVideosTable.scope eq "")))
        } > 0
    }
}
