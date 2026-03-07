package dev.typetype.server.services

import dev.typetype.server.db.tables.TokenTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class TokenService {

    fun getOrGenerate(): String {
        val existing = transaction {
            TokenTable.selectAll().singleOrNull()?.get(TokenTable.value)
        }
        if (existing != null) return existing
        val token = UUID.randomUUID().toString()
        transaction { TokenTable.insert { it[value] = token } }
        return token
    }
}
