package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table("users") {
    val id = text("id")
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val name = text("name")
    val role = text("role") // "admin", "moderator", "user"
    val verified = bool("verified").default(false)
    val suspended = bool("suspended").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}
