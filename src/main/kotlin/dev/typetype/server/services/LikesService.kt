package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.LikesTable
import dev.typetype.server.models.LikeItem
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class LikesService {

    suspend fun getAll(): List<LikeItem> = DatabaseFactory.query {
        LikesTable.selectAll()
            .orderBy(LikesTable.likedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(videoUrl: String): LikeItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            LikesTable.insert {
                it[LikesTable.videoUrl] = videoUrl
                it[likedAt] = now
            }
        }
        return LikeItem(videoUrl = videoUrl, likedAt = now)
    }

    suspend fun delete(videoUrl: String): Boolean = DatabaseFactory.query {
        LikesTable.deleteWhere { LikesTable.videoUrl eq videoUrl } > 0
    }

    private fun ResultRow.toItem() = LikeItem(
        videoUrl = this[LikesTable.videoUrl],
        likedAt = this[LikesTable.likedAt],
    )
}
