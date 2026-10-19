package com.bragadev.fiscal.data.database

import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.model.StoredExpenseCheck
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime

/**
 * Conferências de despesas por arquivo. Pasta e nome em minúsculas (o Windows não diferencia).
 * `missing` guarda os itens que faltam separados por vírgula (ex.: "FISCAL_DOCUMENT,SIGNATURE").
 */
class ExpenseCheckDao(private val database: Database) {

    suspend fun checksIn(folder: String): Map<String, StoredExpenseCheck> = database.use {
        prepareStatement(
            "SELECT file_name, size_bytes, modified_millis, missing, unreadable FROM expense_checks WHERE folder = ?",
        ).use { statement ->
            statement.setString(1, folder)
            statement.executeQuery().use { rows ->
                generateSequence { if (rows.next()) rows.getString("file_name") to rows.toCheck() else null }.toMap()
            }
        }
    }

    suspend fun upsert(folder: String, fileName: String, stored: StoredExpenseCheck) = database.use {
        prepareStatement(
            "INSERT OR REPLACE INTO expense_checks " +
                    "(folder, file_name, size_bytes, modified_millis, missing, unreadable, checked_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, folder)
            statement.setString(2, fileName)
            statement.setLong(3, stored.sizeBytes)
            statement.setLong(4, stored.lastModified.toEpochMilli())
            statement.setString(5, stored.check.missing.joinToString(",") { it.name })
            statement.setInt(6, if (stored.check.unreadable) 1 else 0)
            statement.setString(7, LocalDateTime.now().toString())
            statement.executeUpdate()
        }
        Unit
    }

    private fun ResultSet.toCheck(): StoredExpenseCheck {
        // Nome desconhecido (versão futura) é ignorado em vez de quebrar a leitura.
        val missing = getString("missing").split(",").filter { it.isNotBlank() }
            .mapNotNull { name -> ExpenseRequirement.entries.firstOrNull { it.name == name } }.toSet()
        return StoredExpenseCheck(
            sizeBytes = getLong("size_bytes"),
            lastModified = Instant.ofEpochMilli(getLong("modified_millis")),
            check = ExpenseCheck(missing, unreadable = getInt("unreadable") != 0),
        )
    }
}
