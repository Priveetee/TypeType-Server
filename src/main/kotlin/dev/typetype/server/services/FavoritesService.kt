package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.models.FavoriteItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class FavoritesService {

    suspend fun getAll(userId: String): List<FavoriteItem> = DatabaseFactory.query {
        FavoritesTable.selectAll()
            .where { FavoritesTable.userId eq userId }
            .orderBy(FavoritesTable.favoritedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(userId: String, videoUrl: String): FavoriteItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            FavoritesTable.insert {
                it[FavoritesTable.userId] = userId
                it[FavoritesTable.videoUrl] = videoUrl
                it[favoritedAt] = now
            }
        }
        return FavoriteItem(videoUrl = videoUrl, favoritedAt = now)
    }

    suspend fun delete(userId: String, videoUrl: String): Boolean = DatabaseFactory.query {
        FavoritesTable.deleteWhere { FavoritesTable.videoUrl eq videoUrl and (FavoritesTable.userId eq userId) } > 0
    }

    private fun ResultRow.toItem() = FavoriteItem(
        videoUrl = this[FavoritesTable.videoUrl],
        favoritedAt = this[FavoritesTable.favoritedAt],
    )
}
