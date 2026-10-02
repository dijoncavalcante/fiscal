package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.CategoryNode
import com.bragadev.fiscal.domain.model.DocumentCategory

/** Regras sobre a estrutura hierárquica das categorias. */
class CategoryHierarchy(private val categories: List<DocumentCategory>) {
    private val byId = categories.associateBy { it.id }

    fun find(id: String): DocumentCategory? = byId[id]

    /**
     * Árvore agrupada por conta. Com [account] informado, mostra só aquela conta e "Outros".
     */
    fun tree(account: AccountType? = null): List<AccountGroup> = AccountType.entries
        .filter { account == null || it == account || it.isCatchAll }
        .mapNotNull { type ->
            val roots = categories.filter { it.accountType == type && it.parentId == null }
            if (roots.isEmpty()) null else AccountGroup(type, roots.map(::nodeOf))
        }

    /** Uma categoria pode ser usada numa pasta de mês da conta dela, ou sempre, se for "Outros". */
    fun isAllowedIn(category: DocumentCategory, account: AccountType?): Boolean =
        account == null || category.accountType == account || category.accountType.isCatchAll

    private fun nodeOf(category: DocumentCategory): CategoryNode =
        CategoryNode(category, categories.filter { it.parentId == category.id }.map(::nodeOf))
}
