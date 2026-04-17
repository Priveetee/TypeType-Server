package dev.typetype.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.password4j.Password
import dev.typetype.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.Date

open class AuthService(private val jwtSecret: String, private val hasUsersProbe: (() -> Boolean)? = null) {
    private val accessCodec = AuthAccessTokenCodec(jwtSecret)
    private val sessionStore = AuthSessionStore()
    private val tokenIssuer = AuthTokenIssuer(accessCodec, sessionStore)
    private val sessionRefresher = AuthSessionRefresher(sessionStore, tokenIssuer)
    private val sessionVerifier = AuthSessionVerifier(accessCodec, sessionStore)
    private val sessionRevoker = AuthSessionRevoker(sessionStore)

    fun register(email: String, password: String, name: String): AuthSessionTokens {
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
        return tokenIssuer.issue(userId) ?: throw IllegalStateException("Failed to create session")
    }

    fun login(identifier: String, password: String): AuthSessionTokens? {
        val user = transaction {
            UsersTable.selectAll().where { (UsersTable.email eq identifier) or (UsersTable.publicUsername eq identifier) }.singleOrNull()
        } ?: return null

        val hashed = user[UsersTable.passwordHash]
        val verified = Password.check(password, hashed).withArgon2()
        if (!verified) return null

        return tokenIssuer.issue(user[UsersTable.id])
    }

    fun refreshSession(refreshToken: String): AuthSessionTokens? = sessionRefresher.refresh(refreshToken)

    fun logout(refreshToken: String?) {
        sessionRevoker.revokeByRefreshToken(refreshToken)
    }

    open fun verify(token: String): String? {
        return sessionVerifier.verifyUserId(token)
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
