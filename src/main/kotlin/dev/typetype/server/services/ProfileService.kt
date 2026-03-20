package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.UserProfileItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ProfileService {

    suspend fun getProfile(userId: String): UserProfileItem? = DatabaseFactory.query {
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.let {
            UserProfileItem(
                id = it[UsersTable.id],
                role = it[UsersTable.role],
                avatarUrl = it[UsersTable.avatarUrl],
                avatarType = it[UsersTable.avatarType],
                avatarCode = it[UsersTable.avatarCode],
            )
        }
    }

    suspend fun setEmojiAvatar(userId: String, code: String, avatarService: AvatarService): Boolean {
        val normalized = avatarService.normalizeEmojiCode(code) ?: return false
        val now = System.currentTimeMillis()
        return DatabaseFactory.query {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[avatarType] = "emoji"
                it[avatarCode] = normalized
                it[avatarUrl] = avatarService.openMojiPath(normalized)
                it[updatedAt] = now
            } > 0
        }
    }

    suspend fun setCustomAvatar(userId: String, imageUrl: String, avatarService: AvatarService): Boolean {
        if (!avatarService.isAllowedCustomUrl(imageUrl)) return false
        val now = System.currentTimeMillis()
        return DatabaseFactory.query {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[avatarType] = "custom"
                it[avatarCode] = null
                it[avatarUrl] = imageUrl
                it[updatedAt] = now
            } > 0
        }
    }

    suspend fun clearAvatar(userId: String): Boolean = DatabaseFactory.query {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[avatarType] = null
            it[avatarCode] = null
            it[avatarUrl] = null
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }
}
