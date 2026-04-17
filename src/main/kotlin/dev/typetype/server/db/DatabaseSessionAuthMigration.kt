package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseSessionAuthMigration {
    fun apply() {
        exec("ALTER TABLE sessions ADD COLUMN IF NOT EXISTS refresh_token_hash TEXT")
        exec("ALTER TABLE sessions ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0")
        exec("ALTER TABLE sessions ADD COLUMN IF NOT EXISTS revoked_at BIGINT")
        exec("UPDATE sessions SET created_at = CASE WHEN created_at = 0 THEN expires_at - 86400000 ELSE created_at END")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS sessions_refresh_token_hash_unique ON sessions (refresh_token_hash)")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
