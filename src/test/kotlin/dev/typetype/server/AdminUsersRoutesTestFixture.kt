package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.UserAdminService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal object AdminUsersRoutesTestFixture {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val userAdminService = UserAdminService()
    private val passwordResetService = PasswordResetService()
    private val adminSettingsService = AdminSettingsService()

    fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { adminRoutes(auth, userAdminService, passwordResetService, adminSettingsService) }
        }
        block()
    }

    fun seedUsers() {
        transaction {
            insertUser(TEST_USER_ID, "admin@test.local", "Admin", "admin", 10L)
            repeat(3) { index -> insertUser("user-$index", "user$index@test.local", "User$index", "user", 20L + index) }
        }
    }

    private fun insertUser(id: String, email: String, name: String, role: String, timestamp: Long) {
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = "hash"
            it[UsersTable.name] = name
            it[UsersTable.role] = role
            it[UsersTable.createdAt] = timestamp
            it[UsersTable.updatedAt] = timestamp
        }
    }
}
