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
     * Árvore agrupada por conta, com "Outros" no final de cada conta.
     * Com [account] informado, mostra só aquela conta.
     */
    fun tree(account: AccountType? = null): List<AccountGroup> {
        val catchAll = rootsOf { it.accountType.isCatchAll }
        return AccountType.entries
            .filter { !it.isCatchAll && (account == null || it == account) }
            .mapNotNull { type ->
                val roots = rootsOf { it.accountType == type }
                if (roots.isEmpty()) null else AccountGroup(type, (roots + catchAll).map(::nodeOf))
            }
    }

    private fun rootsOf(filter: (DocumentCategory) -> Boolean): List<DocumentCategory> =
        categories.filter { it.parentId == null && filter(it) }

    /** Uma categoria pode ser usada numa pasta de mês da conta dela, ou sempre, se for "Outros". */
    fun isAllowedIn(category: DocumentCategory, account: AccountType?): Boolean =
        account == null || category.accountType == account || category.accountType.isCatchAll

    private fun nodeOf(category: DocumentCategory): CategoryNode =
        CategoryNode(category, categories.filter { it.parentId == category.id }.map(::nodeOf))
}
