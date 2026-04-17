package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.models.ProgressItem
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ProgressService {

    suspend fun get(userId: String, videoUrl: String): ProgressItem? = DatabaseFactory.query {
        ProgressTable.selectAll()
            .where { (ProgressTable.videoUrl eq videoUrl) and (ProgressTable.userId eq userId) }
            .singleOrNull()
            ?.let {
                ProgressItem(
                    videoUrl = it[ProgressTable.videoUrl],
                    position = it[ProgressTable.position],
                    updatedAt = it[ProgressTable.updatedAt],
                )
            }
    }

    suspend fun upsert(userId: String, videoUrl: String, position: Long): ProgressItem {
        val now = System.currentTimeMillis()
        val safePosition = position.coerceAtLeast(0L)
        val current = get(userId, videoUrl)?.position ?: 0L
        val next = if (safePosition == 0L && current > 0L) current else maxOf(current, safePosition)
        DatabaseFactory.query {
            val updated = ProgressTable.update({ (ProgressTable.videoUrl eq videoUrl) and (ProgressTable.userId eq userId) }) {
                it[ProgressTable.position] = next
                it[updatedAt] = now
            }
            if (updated == 0) {
                ProgressTable.insert {
                    it[ProgressTable.userId] = userId
                    it[ProgressTable.videoUrl] = videoUrl
                    it[ProgressTable.position] = next
                    it[updatedAt] = now
                }
            }
        }
        return ProgressItem(videoUrl = videoUrl, position = next, updatedAt = now)
    }
}
