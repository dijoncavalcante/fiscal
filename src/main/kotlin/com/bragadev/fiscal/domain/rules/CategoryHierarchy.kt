package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.CategoryNode
import com.bragadev.fiscal.domain.model.DocumentCategory

/** Regras sobre a estrutura hierárquica das categorias. */
class CategoryHierarchy(private val categories: List<DocumentCategory>) {
    private val byId = categories.associateBy { it.id }
    private val catchAll = rootsOf { it.accountType.isCatchAll }

    fun find(id: String): DocumentCategory? = byId[id]

    /**
     * Árvore agrupada por conta, com "Outros" no final de cada conta.
     * Se a conta tem a sua própria categoria com o mesmo nome (ex.: "6. Outros" da Manutenção),
     * ela substitui o "Outros" geral. Com [account] informado, mostra só aquela conta.
     */
    fun tree(account: AccountType? = null): List<AccountGroup> = AccountType.entries
        .filter { !it.isCatchAll && (account == null || it == account) }
        .mapNotNull { type ->
            val roots = rootsOf { it.accountType == type }
            if (roots.isEmpty()) null else AccountGroup(type, (roots + catchAllFor(type)).map(::nodeOf))
        }

    /** Uma categoria pode ser usada numa pasta de mês da conta dela, ou o "Outros" geral, se a conta não tiver o seu. */
    fun isAllowedIn(category: DocumentCategory, account: AccountType?): Boolean = when {
        account == null -> true
        category.accountType.isCatchAll -> category in catchAllFor(account)
        else -> category.accountType == account
    }

    private fun catchAllFor(account: AccountType): List<DocumentCategory> {
        val ownNames = rootsOf { it.accountType == account }.map { it.name.lowercase() }.toSet()
        return catchAll.filterNot { it.name.lowercase() in ownNames }
    }

    private fun rootsOf(filter: (DocumentCategory) -> Boolean): List<DocumentCategory> =
        categories.filter { it.parentId == null && filter(it) }

    private fun nodeOf(category: DocumentCategory): CategoryNode =
        CategoryNode(category, categories.filter { it.parentId == category.id }.map(::nodeOf))
}
