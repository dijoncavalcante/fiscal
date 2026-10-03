package com.bragadev.fiscal.data.database

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.NamingRule
import java.sql.ResultSet

class CategoryDao(private val database: Database) {

    /**
     * Grava as categorias padrão, atualizando as já existentes para que mudanças nas regras
     * (ex.: Despesas passar a aceitar vários arquivos) cheguem a bancos criados antes.
     */
    suspend fun upsert(categories: List<DocumentCategory>) = database.use {
        prepareStatement(
            "INSERT INTO categories " +
                "(id, name, number, account_type, parent_id, naming_rule, sort_order, file_base_name, optional) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET name = excluded.name, number = excluded.number, " +
                "account_type = excluded.account_type, parent_id = excluded.parent_id, " +
                "naming_rule = excluded.naming_rule, sort_order = excluded.sort_order, " +
                "file_base_name = excluded.file_base_name, optional = excluded.optional",
        ).use { statement ->
            categories.forEachIndexed { index, category ->
                statement.setString(1, category.id)
                statement.setString(2, category.name)
                statement.setString(3, category.number)
                statement.setString(4, category.accountType.name)
                statement.setString(5, category.parentId)
                statement.setString(6, category.namingRule.name)
                statement.setInt(7, index)
                statement.setString(8, category.fileBaseName)
                statement.setInt(9, if (category.optional) 1 else 0)
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
        fileBaseName = getString("file_base_name"),
        optional = getInt("optional") == 1,
    )
}
