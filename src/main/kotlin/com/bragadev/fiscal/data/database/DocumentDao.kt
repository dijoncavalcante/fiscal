package com.bragadev.fiscal.data.database

import java.time.LocalDateTime

class DocumentDao(private val database: Database) {

    /**
     * Registra a nova localização do documento. Se ele já era conhecido pelo caminho antigo,
     * atualiza o registro mantendo o nome e o caminho originais.
     */
    suspend fun upsertMove(fromPath: String, toPath: String, toName: String, categoryId: String?) = database.use {
        val now = LocalDateTime.now().toString()
        val updated = prepareStatement(
            "UPDATE documents SET current_path = ?, current_name = ?, category_id = ?, updated_at = ? WHERE current_path = ?",
        ).use { statement ->
            statement.setString(1, toPath)
            statement.setString(2, toName)
            statement.setString(3, categoryId)
            statement.setString(4, now)
            statement.setString(5, fromPath)
            statement.executeUpdate()
        }
        if (updated == 0) {
            prepareStatement(
                "INSERT OR REPLACE INTO documents " +
                    "(original_name, current_name, original_path, current_path, category_id, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, fromPath.substringAfterLast('\\').substringAfterLast('/'))
                statement.setString(2, toName)
                statement.setString(3, fromPath)
                statement.setString(4, toPath)
                statement.setString(5, categoryId)
                statement.setString(6, now)
                statement.setString(7, now)
                statement.executeUpdate()
            }
        }
        Unit
    }
}
