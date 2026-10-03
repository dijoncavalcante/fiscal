package com.bragadev.fiscal.data.database

import java.time.LocalDateTime

/**
 * Pendências por arquivo. Pasta e nome são gravados em minúsculas, pois o Windows
 * não diferencia maiúsculas de minúsculas nos caminhos.
 */
class FileFlagDao(private val database: Database) {

    suspend fun notesIn(folder: String): Map<String, String> = database.use {
        prepareStatement("SELECT file_name, note FROM file_flags WHERE folder = ?").use { statement ->
            statement.setString(1, folder)
            statement.executeQuery().use { rows ->
                generateSequence { if (rows.next()) rows.getString("file_name") to rows.getString("note") else null }.toMap()
            }
        }
    }

    suspend fun upsert(folder: String, fileName: String, note: String) = database.use {
        prepareStatement(
            "INSERT INTO file_flags (folder, file_name, note, created_at) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(folder, file_name) DO UPDATE SET note = excluded.note",
        ).use { statement ->
            statement.setString(1, folder)
            statement.setString(2, fileName)
            statement.setString(3, note)
            statement.setString(4, LocalDateTime.now().toString())
            statement.executeUpdate()
        }
        Unit
    }

    suspend fun delete(folder: String, fileName: String) = database.use {
        prepareStatement("DELETE FROM file_flags WHERE folder = ? AND file_name = ?").use { statement ->
            statement.setString(1, folder)
            statement.setString(2, fileName)
            statement.executeUpdate()
        }
        Unit
    }

    suspend fun move(fromFolder: String, fromName: String, toFolder: String, toName: String) = database.use {
        prepareStatement(
            "UPDATE OR REPLACE file_flags SET folder = ?, file_name = ? WHERE folder = ? AND file_name = ?",
        ).use { statement ->
            statement.setString(1, toFolder)
            statement.setString(2, toName)
            statement.setString(3, fromFolder)
            statement.setString(4, fromName)
            statement.executeUpdate()
        }
        Unit
    }
}
