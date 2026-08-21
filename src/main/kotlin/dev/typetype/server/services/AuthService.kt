package dev.typetype.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.password4j.Password
import dev.typetype.server.db.tables.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.Date

open class AuthService(
    private val jwtSecret: String,
    private val hasUsersProbe: (() -> Boolean)? = null,
    sessionConfig: AuthSessionConfig = AuthSessionConfig(),
) {
    private val accessCodec = AuthAccessTokenCodec(jwtSecret)
    private val sessionStore = AuthSessionStore()
    private val tokenIssuer = AuthTokenIssuer(accessCodec, sessionStore, sessionConfig)
    private val sessionRefresher = AuthSessionRefresher(sessionStore, tokenIssuer)
    private val sessionVerifier = AuthSessionVerifier(accessCodec, sessionStore)
    private val sessionRevoker = AuthSessionRevoker(sessionStore)

    suspend fun register(email: String, password: String, name: String): AuthSessionTokens = withContext(Dispatchers.IO) {
        val hashed = Password.hash(password).withArgon2().result
        val userId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val needsAdmin = !hasAdminBlocking()
        val role = if (needsAdmin) "admin" else "user"
        val publicUsername = name.trim().takeIf(ProfileService::isValidPublicUsername)

        transaction {
            UsersTable.insert {
                it[UsersTable.id] = userId
                it[UsersTable.email] = email
                it[UsersTable.passwordHash] = hashed
                it[UsersTable.name] = name
                it[UsersTable.publicUsername] = publicUsername
                it[UsersTable.role] = role
                it[UsersTable.createdAt] = now
                it[UsersTable.updatedAt] = now
            }
        }
        tokenIssuer.issue(userId) ?: throw IllegalStateException("Failed to create session")
    }

    suspend fun login(identifier: String, password: String): AuthSessionTokens? = withContext(Dispatchers.IO) {
        val normalizedIdentifier = identifier.trim().lowercase()
        if (normalizedIdentifier.isBlank()) return@withContext null
        val user = transaction {
            val query = UsersTable.selectAll().where {
                if (normalizedIdentifier.contains("@")) {
                    UsersTable.email.lowerCase() eq normalizedIdentifier
                } else {
                    UsersTable.publicUsername.lowerCase() eq normalizedIdentifier
                }
            }
            query.singleOrNull()
        } ?: return@withContext null

        val hashed = user[UsersTable.passwordHash]
        val verified = Password.check(password, hashed).withArgon2()
        if (!verified) return@withContext null

        tokenIssuer.issue(user[UsersTable.id])
    }

    suspend fun refreshSession(refreshToken: String): AuthSessionTokens? = withContext(Dispatchers.IO) {
        sessionRefresher.refresh(refreshToken)
    }

    suspend fun issueSession(userId: String): AuthSessionTokens? = withContext(Dispatchers.IO) {
        tokenIssuer.issue(userId)
    }

    suspend fun logout(refreshToken: String?): Unit = withContext(Dispatchers.IO) {
        sessionRevoker.revokeByRefreshToken(refreshToken)
    }

    open suspend fun verify(token: String): String? = withContext(Dispatchers.IO) {
        sessionVerifier.verifyUserId(token)
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

    suspend fun getUserRole(userId: String): String? = withContext(Dispatchers.IO) {
        if (userId.startsWith("guest:")) return@withContext "user"
        transaction {
            UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()
        }?.get(UsersTable.role)
    }

    suspend fun hasUsers(): Boolean = withContext(Dispatchers.IO) {
        hasUsersProbe?.invoke() ?: transaction { UsersTable.selectAll().empty().not() }
    }

    suspend fun hasAdmin(): Boolean = withContext(Dispatchers.IO) { hasAdminBlocking() }

    private fun hasAdminBlocking(): Boolean = hasUsersProbe?.invoke() ?: transaction {
        UsersTable.selectAll().where { UsersTable.role eq "admin" }.empty().not()
    }

    companion object {
        private const val GUEST_TTL_MS = 7 * 24 * 60 * 60 * 1000L

        fun fixed(userId: String): AuthService = object : AuthService("test") {
            override suspend fun verify(token: String): String? = if (token == "test-jwt") userId else null
        }

        fun fixed(userId: String, hasUsers: Boolean): AuthService = object : AuthService("test", { hasUsers }) {
            override suspend fun verify(token: String): String? = if (token == "test-jwt") userId else null
        }
    }
}
