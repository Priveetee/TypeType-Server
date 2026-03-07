package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.models.BlockedItem
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class BlockedService {

    suspend fun getChannels(): List<BlockedItem> = DatabaseFactory.query {
        BlockedChannelsTable.selectAll()
            .orderBy(BlockedChannelsTable.blockedAt to SortOrder.DESC)
            .map { BlockedItem(url = it[BlockedChannelsTable.channelUrl], blockedAt = it[BlockedChannelsTable.blockedAt]) }
    }

    suspend fun addChannel(url: String): BlockedItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query { BlockedChannelsTable.insert { it[channelUrl] = url; it[blockedAt] = now } }
        return BlockedItem(url = url, blockedAt = now)
    }

    suspend fun deleteChannel(url: String): Boolean = DatabaseFactory.query {
        BlockedChannelsTable.deleteWhere { channelUrl eq url } > 0
    }

    suspend fun getVideos(): List<BlockedItem> = DatabaseFactory.query {
        BlockedVideosTable.selectAll()
            .orderBy(BlockedVideosTable.blockedAt to SortOrder.DESC)
            .map { BlockedItem(url = it[BlockedVideosTable.videoUrl], blockedAt = it[BlockedVideosTable.blockedAt]) }
    }

    suspend fun addVideo(url: String): BlockedItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query { BlockedVideosTable.insert { it[videoUrl] = url; it[blockedAt] = now } }
        return BlockedItem(url = url, blockedAt = now)
    }

    suspend fun deleteVideo(url: String): Boolean = DatabaseFactory.query {
        BlockedVideosTable.deleteWhere { videoUrl eq url } > 0
    }
}
