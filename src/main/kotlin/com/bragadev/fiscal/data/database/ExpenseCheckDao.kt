package com.bragadev.fiscal.data.database

import com.bragadev.fiscal.domain.model.*
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime

/**
 * Conferências de despesas por arquivo. Pasta e nome em minúsculas (o Windows não diferencia).
 * `missing` guarda os itens que faltam separados por vírgula (ex.: "FISCAL_DOCUMENT,SIGNATURE").
 * `amount_problem`: problema no valor (ex.: "MISMATCH"); `paid_amount`/`fiscal_amount`: valores lidos (texto,
 * ex.: "125.00"). Linhas da versão 2 não têm `amount_problem`: valor gravado = diferença ("MISMATCH").
 * `amount_evidence`: de onde veio o total quando o valor bateu (texto mostrado no "certo" verde).
 */
class ExpenseCheckDao(private val database: Database) {

    suspend fun checksIn(folder: String): Map<String, StoredExpenseCheck> = database.use {
        prepareStatement(
            "SELECT file_name, size_bytes, modified_millis, missing, unreadable, amount_problem, paid_amount, " +
                    "fiscal_amount, amount_evidence, rules_version FROM expense_checks WHERE folder = ?",
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
                    "(folder, file_name, size_bytes, modified_millis, missing, unreadable, amount_problem, paid_amount, " +
                    "fiscal_amount, amount_evidence, rules_version, checked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            val problem = stored.check.amountProblem
            statement.setString(1, folder)
            statement.setString(2, fileName)
            statement.setLong(3, stored.sizeBytes)
            statement.setLong(4, stored.lastModified.toEpochMilli())
            statement.setString(5, stored.check.missing.joinToString(",") { it.name })
            statement.setInt(6, if (stored.check.unreadable) 1 else 0)
            statement.setString(7, problem?.kind?.name)
            statement.setString(8, problem?.paid?.toPlainString())
            statement.setString(9, problem?.fiscal?.toPlainString())
            statement.setString(10, stored.check.amountEvidence)
            statement.setInt(11, stored.rulesVersion)
            statement.setString(12, LocalDateTime.now().toString())
            statement.executeUpdate()
        }
        Unit
    }

    private fun ResultSet.toCheck(): StoredExpenseCheck {
        // Nome desconhecido (versão futura) é ignorado em vez de quebrar a leitura.
        val missing = getString("missing").split(",").filter { it.isNotBlank() }
            .mapNotNull { name -> ExpenseRequirement.entries.firstOrNull { it.name == name } }.toSet()
        val paid = getString("paid_amount")?.let(::BigDecimal)
        val kind =
            getString("amount_problem")?.let { name -> AmountProblemKind.entries.firstOrNull { it.name == name } }
                ?: AmountProblemKind.MISMATCH.takeIf { paid != null }
        return StoredExpenseCheck(
            sizeBytes = getLong("size_bytes"),
            lastModified = Instant.ofEpochMilli(getLong("modified_millis")),
            check = ExpenseCheck(
                missing,
                unreadable = getInt("unreadable") != 0,
                amountProblem = kind?.let { AmountProblem(it, paid, getString("fiscal_amount")?.let(::BigDecimal)) },
                amountEvidence = getString("amount_evidence"),
            ),
            rulesVersion = getInt("rules_version"),
        )
    }
}
