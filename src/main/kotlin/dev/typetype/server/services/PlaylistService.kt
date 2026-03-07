package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class PlaylistService {

    suspend fun getAll(): List<PlaylistItem> = DatabaseFactory.query {
        PlaylistsTable.selectAll()
            .orderBy(PlaylistsTable.createdAt to SortOrder.DESC)
            .map { PlaylistItem(id = it[PlaylistsTable.id], name = it[PlaylistsTable.name], description = it[PlaylistsTable.description], createdAt = it[PlaylistsTable.createdAt]) }
    }

    suspend fun getById(id: String): PlaylistItem? = DatabaseFactory.query {
        val row = PlaylistsTable.selectAll().where { PlaylistsTable.id eq id }.singleOrNull() ?: return@query null
        val videos = PlaylistVideosTable.selectAll()
            .where { PlaylistVideosTable.playlistId eq id }
            .orderBy(PlaylistVideosTable.position to SortOrder.ASC)
            .map { it.toVideoItem() }
        PlaylistItem(id = row[PlaylistsTable.id], name = row[PlaylistsTable.name], description = row[PlaylistsTable.description], videos = videos, createdAt = row[PlaylistsTable.createdAt])
    }

    suspend fun create(item: PlaylistItem): PlaylistItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            PlaylistsTable.insert {
                it[PlaylistsTable.id] = id
                it[name] = item.name
                it[description] = item.description
                it[createdAt] = now
            }
        }
        return item.copy(id = id, createdAt = now, videos = emptyList())
    }

    suspend fun update(id: String, item: PlaylistItem): Boolean = DatabaseFactory.query {
        PlaylistsTable.update({ PlaylistsTable.id eq id }) {
            it[name] = item.name
            it[description] = item.description
        } > 0
    }

    suspend fun delete(id: String): Boolean = DatabaseFactory.query {
        PlaylistsTable.deleteWhere { PlaylistsTable.id eq id } > 0
    }

    suspend fun addVideo(playlistId: String, video: PlaylistVideoItem): PlaylistVideoItem {
        val videoId = UUID.randomUUID().toString()
        val pos = DatabaseFactory.query { PlaylistVideosTable.selectAll().where { PlaylistVideosTable.playlistId eq playlistId }.count().toInt() }
        DatabaseFactory.query {
            PlaylistVideosTable.insert {
                it[PlaylistVideosTable.id] = videoId
                it[PlaylistVideosTable.playlistId] = playlistId
                it[url] = video.url
                it[title] = video.title
                it[thumbnail] = video.thumbnail
                it[duration] = video.duration
                it[position] = pos
            }
        }
        return video.copy(id = videoId, position = pos)
    }

    suspend fun removeVideo(playlistId: String, videoUrl: String): Boolean = DatabaseFactory.query {
        PlaylistVideosTable.deleteWhere { (PlaylistVideosTable.playlistId eq playlistId) and (PlaylistVideosTable.url eq videoUrl) } > 0
    }

    private fun ResultRow.toVideoItem() = PlaylistVideoItem(
        id = this[PlaylistVideosTable.id],
        url = this[PlaylistVideosTable.url],
        title = this[PlaylistVideosTable.title],
        thumbnail = this[PlaylistVideosTable.thumbnail],
        duration = this[PlaylistVideosTable.duration],
        position = this[PlaylistVideosTable.position],
    )
}
