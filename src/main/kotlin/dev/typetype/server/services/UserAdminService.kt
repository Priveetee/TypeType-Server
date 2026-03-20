package dev.typetype.server.services

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminUserItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class UserAdminService {

    fun listUsers(): List<AdminUserItem> = transaction {
        UsersTable.selectAll()
            .orderBy(UsersTable.createdAt to SortOrder.DESC)
            .map(::toAdminUserItem)
    }

    fun listUsers(page: Int, limit: Int): Pair<List<AdminUserItem>, Long> = transaction {
        val total = UsersTable.selectAll().count()
        val offset = (page - 1).toLong() * limit.toLong()
        val users = UsersTable.selectAll()
            .orderBy(UsersTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map(::toAdminUserItem)
        users to total
    }

    fun suspendUser(userId: String): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.suspended] = true
            it[UsersTable.updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun unsuspendUser(userId: String): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.suspended] = false
            it[UsersTable.updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun promoteUser(userId: String, role: String): Boolean {
        if (role !in setOf("user", "moderator", "admin")) return false
        return transaction {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.role] = role
                it[UsersTable.updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    fun deleteUser(userId: String): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.suspended] = true
            it[UsersTable.updatedAt] = System.currentTimeMillis()
        } > 0
    }

    private fun toAdminUserItem(row: ResultRow): AdminUserItem =
        AdminUserItem(
            id = row[UsersTable.id],
            email = row[UsersTable.email],
            name = row[UsersTable.name],
            role = row[UsersTable.role],
            publicUsername = row[UsersTable.publicUsername],
            bio = row[UsersTable.bio],
            avatarUrl = row[UsersTable.avatarUrl],
            avatarType = row[UsersTable.avatarType],
            avatarCode = row[UsersTable.avatarCode],
            suspended = row[UsersTable.suspended],
            verified = row[UsersTable.verified],
            createdAt = row[UsersTable.createdAt],
        )
}
