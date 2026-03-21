package dev.typetype.server

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsPrimaryKeyMigrationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() {
            TestDatabase.setup()
        }
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `settings primary key migrates to user_id`() {
        val columns = transaction {
            val result = mutableListOf<String>()
            val sql = """
                SELECT a.attname
                FROM pg_index i
                JOIN pg_class t ON t.oid = i.indrelid
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(i.indkey)
                WHERE t.relname = 'settings' AND i.indisprimary
                ORDER BY array_position(i.indkey, a.attnum)
            """.trimIndent()
            exec(sql) { rs ->
                while (rs.next()) result += rs.getString("attname")
            }
            result
        }
        assertEquals(listOf("user_id"), columns)
    }
}
