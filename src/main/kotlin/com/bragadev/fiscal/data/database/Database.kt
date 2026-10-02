package com.bragadev.fiscal.data.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Banco SQLite local. Todo acesso passa por uma única thread de IO,
 * o que evita concorrência na conexão e mantém a UI livre.
 */
class Database(private val file: Path) : AutoCloseable {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val lazyConnection = lazy {
        DriverManager.getConnection("jdbc:sqlite:$file").also(::createSchema)
    }

    suspend fun <T> use(block: Connection.() -> T): T = withContext(dispatcher) { lazyConnection.value.block() }

    override fun close() {
        if (lazyConnection.isInitialized()) lazyConnection.value.close()
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            Schema.statements.forEach(statement::executeUpdate)
        }
    }
}

private object Schema {
    val statements = listOf(
        """
        CREATE TABLE IF NOT EXISTS documents (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            original_name TEXT NOT NULL,
            current_name TEXT NOT NULL,
            original_path TEXT NOT NULL,
            current_path TEXT NOT NULL UNIQUE,
            category_id TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS categories (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            number TEXT NOT NULL,
            account_type TEXT NOT NULL,
            parent_id TEXT,
            naming_rule TEXT NOT NULL,
            sort_order INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS file_operations (
            id TEXT PRIMARY KEY,
            original_path TEXT NOT NULL,
            original_name TEXT NOT NULL,
            new_path TEXT NOT NULL,
            new_name TEXT NOT NULL,
            operation_type TEXT NOT NULL,
            backup_path TEXT,
            undone INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """,
    )
}
