package dev.typetype.server.services

import dev.typetype.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class UserAdminService {

    fun listUsers(): List<Map<String, Any>> = transaction {
        UsersTable.selectAll().map {
            mapOf(
                "id" to it[UsersTable.id],
                "email" to it[UsersTable.email],
                "name" to it[UsersTable.name],
                "role" to it[UsersTable.role],
                "suspended" to it[UsersTable.suspended],
                "verified" to it[UsersTable.verified],
                "createdAt" to it[UsersTable.createdAt]
            )
        }
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
}
