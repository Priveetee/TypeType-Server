package dev.typetype.server

import dev.typetype.server.db.tables.PasswordResetTable
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PasswordResetServiceCoreTest {
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
    fun `reset password updates credentials and token cannot be reused`() {
        val auth = AuthService("test-secret")
        val reset = PasswordResetService()
        val oldPassword = "secret-1"
        val email = "reset@test.local"
        val userId = auth.verify(auth.register(email, oldPassword, "Reset User"))
        assertNotNull(userId)

        val token = reset.generateToken(userId!!)
        assertTrue(reset.resetPassword(token, "secret-2"))
        assertNull(auth.login(email, oldPassword))
        assertNotNull(auth.login(email, "secret-2"))
        assertFalse(reset.resetPassword(token, "secret-3"))
    }

    @Test
    fun `expired token is rejected`() {
        val auth = AuthService("test-secret")
        val reset = PasswordResetService()
        val userId = auth.verify(auth.register("expired@test.local", "secret-1", "Expired"))
        val token = reset.generateToken(userId!!)

        transaction {
            PasswordResetTable.update({ PasswordResetTable.token eq token }) {
                it[expiresAt] = System.currentTimeMillis() - 1
            }
        }

        assertFalse(reset.resetPassword(token, "secret-2"))
    }

    @Test
    fun `unknown token is rejected`() {
        val reset = PasswordResetService()
        assertFalse(reset.resetPassword("missing-token", "secret-2"))
    }
}
