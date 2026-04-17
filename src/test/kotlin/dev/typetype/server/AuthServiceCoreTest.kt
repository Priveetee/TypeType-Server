package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.services.AuthService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthServiceCoreTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `register sets first admin and second user`() {
        val service = AuthService("test-secret")
        assertFalse(service.hasUsers())

        val session1 = service.register("first@test.local", "secret-1", "First")
        val user1 = service.verify(session1.accessToken)
        assertNotNull(user1)
        assertEquals("admin", user1?.let { roleOf(it) })
        assertTrue(service.hasUsers())

        val session2 = service.register("second@test.local", "secret-2", "Second")
        val user2 = service.verify(session2.accessToken)
        assertNotNull(user2)
        assertEquals("user", user2?.let { roleOf(it) })
    }

    @Test
    fun `login and refresh token keep same user`() {
        val service = AuthService("test-secret")
        val registered = service.register("login@test.local", "secret-1", "Login")
        val expectedUser = service.verify(registered.accessToken)

        val loginToken = service.login("login@test.local", "secret-1")
        assertNotNull(loginToken)
        assertEquals(expectedUser, loginToken?.let { service.verify(it.accessToken) })
        assertNull(service.login("login@test.local", "wrong"))

        val refreshed = service.refreshSession(registered.refreshToken)
        assertNotNull(refreshed)
        assertEquals(expectedUser, refreshed?.let { service.verify(it.accessToken) })
    }

    @Test
    fun `login supports public username identifier`() {
        val service = AuthService("test-secret")
        val session = service.register("username@test.local", "secret-1", "User")
        val userId = service.verify(session.accessToken) ?: error("missing user id")
        transaction {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[publicUsername] = "InfinityLoop1308"
            }
        }
        val byUsername = service.login("InfinityLoop1308", "secret-1")
        assertNotNull(byUsername)
        assertEquals(userId, byUsername?.let { service.verify(it.accessToken) })
    }

    @Test
    fun `guest token verifies and has user role`() {
        val service = AuthService("test-secret")
        val guestToken = service.guestLogin()
        val guestId = service.verify(guestToken)
        assertNotNull(guestId)
        assertTrue(guestId?.startsWith("guest:") == true)
        assertEquals("user", guestId?.let { service.getUserRole(it) })
    }

    private fun roleOf(userId: String): String? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.role)
    }
}
