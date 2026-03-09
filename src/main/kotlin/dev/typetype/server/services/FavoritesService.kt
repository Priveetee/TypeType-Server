package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.models.FavoriteItem
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class FavoritesService {

    suspend fun getAll(): List<FavoriteItem> = DatabaseFactory.query {
        FavoritesTable.selectAll()
            .orderBy(FavoritesTable.favoritedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(videoUrl: String): FavoriteItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            FavoritesTable.insert {
                it[FavoritesTable.videoUrl] = videoUrl
                it[favoritedAt] = now
            }
        }
        return FavoriteItem(videoUrl = videoUrl, favoritedAt = now)
    }

    suspend fun delete(videoUrl: String): Boolean = DatabaseFactory.query {
        FavoritesTable.deleteWhere { FavoritesTable.videoUrl eq videoUrl } > 0
    }

    private fun ResultRow.toItem() = FavoriteItem(
        videoUrl = this[FavoritesTable.videoUrl],
        favoritedAt = this[FavoritesTable.favoritedAt],
    )
}
