package dev.typetype.server.services

import dev.typetype.server.db.tables.TokenTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class TokenService {

    @Volatile private var cached: String? = null

    fun getOrGenerate(): String = cached ?: resolve().also { cached = it }

    private fun resolve(): String {
        val existing = transaction {
            TokenTable.selectAll().singleOrNull()?.get(TokenTable.value)
        }
        if (existing != null) return existing
        val token = UUID.randomUUID().toString()
        transaction { TokenTable.insert { it[value] = token } }
        return token
    }

    companion object {
        fun fixed(token: String): TokenService = TokenService().also { it.cached = token }
    }
}
