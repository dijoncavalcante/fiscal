package com.bragadev.fiscal.data.database

import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.OperationType
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.UUID

class FileOperationDao(private val database: Database) {

    suspend fun insert(operation: FileOperation) = database.use {
        prepareStatement(
            "INSERT INTO file_operations " +
                "(id, original_path, original_name, new_path, new_name, operation_type, backup_path, undone, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, operation.id.toString())
            statement.setString(2, operation.originalPath)
            statement.setString(3, operation.originalName)
            statement.setString(4, operation.newPath)
            statement.setString(5, operation.newName)
            statement.setString(6, operation.type.name)
            statement.setString(7, operation.backupPath)
            statement.setInt(8, if (operation.undone) 1 else 0)
            statement.setString(9, operation.timestamp.toString())
            statement.executeUpdate()
        }
        Unit
    }

    suspend fun findById(id: UUID): FileOperation? = database.use {
        prepareStatement("SELECT * FROM file_operations WHERE id = ?").use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { rows -> if (rows.next()) rows.toOperation() else null }
        }
    }

    suspend fun markUndone(id: UUID) = database.use {
        prepareStatement("UPDATE file_operations SET undone = 1 WHERE id = ?").use { statement ->
            statement.setString(1, id.toString())
            statement.executeUpdate()
        }
        Unit
    }

    suspend fun lastNotUndone(): FileOperation? = database.use {
        createStatement().use { statement ->
            statement.executeQuery(
                "SELECT * FROM file_operations WHERE undone = 0 ORDER BY created_at DESC, rowid DESC LIMIT 1",
            ).use { rows -> if (rows.next()) rows.toOperation() else null }
        }
    }

    private fun ResultSet.toOperation() = FileOperation(
        id = UUID.fromString(getString("id")),
        type = OperationType.valueOf(getString("operation_type")),
        originalPath = getString("original_path"),
        originalName = getString("original_name"),
        newPath = getString("new_path"),
        newName = getString("new_name"),
        timestamp = LocalDateTime.parse(getString("created_at")),
        backupPath = getString("backup_path"),
        undone = getInt("undone") == 1,
    )
}
