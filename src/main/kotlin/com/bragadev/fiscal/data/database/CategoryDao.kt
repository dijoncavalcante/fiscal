package com.bragadev.fiscal.data.database

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.NamingRule
import java.sql.ResultSet

class CategoryDao(private val database: Database) {

    /** Insere as categorias que ainda não existem, preservando alterações já gravadas. */
    suspend fun insertMissing(categories: List<DocumentCategory>) = database.use {
        prepareStatement(
            "INSERT OR IGNORE INTO categories (id, name, number, account_type, parent_id, naming_rule, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            categories.forEachIndexed { index, category ->
                statement.setString(1, category.id)
                statement.setString(2, category.name)
                statement.setString(3, category.number)
                statement.setString(4, category.accountType.name)
                statement.setString(5, category.parentId)
                statement.setString(6, category.namingRule.name)
                statement.setInt(7, index)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        Unit
    }

    suspend fun getAll(): List<DocumentCategory> = database.use {
        createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM categories ORDER BY sort_order").use { rows ->
                generateSequence { if (rows.next()) rows.toCategory() else null }.toList()
            }
        }
    }

    private fun ResultSet.toCategory() = DocumentCategory(
        id = getString("id"),
        name = getString("name"),
        number = getString("number"),
        accountType = AccountType.valueOf(getString("account_type")),
        parentId = getString("parent_id"),
        namingRule = NamingRule.valueOf(getString("naming_rule")),
    )
}
