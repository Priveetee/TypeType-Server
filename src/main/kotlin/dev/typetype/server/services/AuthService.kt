package dev.typetype.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.password4j.Password
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SessionsTable
import dev.typetype.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.Date

open class AuthService(private val jwtSecret: String, private val hasUsersProbe: (() -> Boolean)? = null) {

    fun register(email: String, password: String, name: String): String {
        val hashed = Password.hash(password).withArgon2().result
        val userId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val isFirstUser = transaction { UsersTable.selectAll().empty() }
        val role = if (isFirstUser) "admin" else "user"

        transaction {
            UsersTable.insert {
                it[UsersTable.id] = userId
                it[UsersTable.email] = email
                it[UsersTable.passwordHash] = hashed
                it[UsersTable.name] = name
                it[UsersTable.role] = role
                it[UsersTable.createdAt] = now
                it[UsersTable.updatedAt] = now
            }
        }
        return createToken(userId)
    }

    fun login(email: String, password: String): String? {
        val user = transaction {
            UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
        } ?: return null

        val hashed = user[UsersTable.passwordHash]
        val verified = Password.check(password, hashed).withArgon2()
        if (!verified) return null

        return createToken(user[UsersTable.id])
    }

    fun refreshToken(oldToken: String): String? {
        val userId = transaction {
            SessionsTable.selectAll().where { SessionsTable.token eq oldToken }.singleOrNull()
        }?.get(SessionsTable.userId) ?: return null
        return createToken(userId)
    }

    private fun createToken(userId: String): String {
        val expiresAt = Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)
        val tokenId = UUID.randomUUID().toString()
        val token = JWT.create()
            .withJWTId(tokenId)
            .withSubject(userId)
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(jwtSecret))
        val sessionId = UUID.randomUUID().toString()
        transaction {
            SessionsTable.insert {
                it[SessionsTable.id] = sessionId
                it[SessionsTable.userId] = userId
                it[SessionsTable.token] = token
                it[SessionsTable.expiresAt] = expiresAt.time
            }
        }
        return token
    }

    open fun verify(token: String): String? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(jwtSecret)).build()
            val decoded = verifier.verify(token)
            decoded.subject
        } catch (e: Exception) {
            null
        }
    }

    fun guestLogin(): String {
        val guestId = "guest:${UUID.randomUUID()}"
        val expiresAt = Date(System.currentTimeMillis() + GUEST_TTL_MS)
        return JWT.create()
            .withJWTId(UUID.randomUUID().toString())
            .withSubject(guestId)
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    fun getUserRole(userId: String): String? {
        if (userId.startsWith("guest:")) return "user"
        return transaction {
            UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()
        }?.get(UsersTable.role)
    }

    fun hasUsers(): Boolean = hasUsersProbe?.invoke() ?: transaction { UsersTable.selectAll().empty().not() }

    companion object {
        private const val GUEST_TTL_MS = 7 * 24 * 60 * 60 * 1000L

        fun fixed(userId: String): AuthService = object : AuthService("test") {
            override fun verify(token: String): String? = if (token == "test-jwt") userId else null
        }

        fun fixed(userId: String, hasUsers: Boolean): AuthService = object : AuthService("test", { hasUsers }) {
            override fun verify(token: String): String? = if (token == "test-jwt") userId else null
        }
    }
}
