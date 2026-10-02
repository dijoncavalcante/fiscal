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
     * Pastas, a partir da raiz, onde os documentos da categoria são guardados.
     *
     * "5.1 Comprovante Remessa" → ["Conta da Congregação", "5. Remessa Betel", "5.1 Comprovante Remessa"]
     */
    fun folderSegments(category: DocumentCategory): List<String> {
        val chain = ancestorsOf(category).reversed() + category
        return listOfNotNull(category.accountType.folderName) + chain.map { it.folderName }
    }

    fun tree(): List<AccountGroup> = AccountType.entries.mapNotNull { account ->
        val roots = categories.filter { it.accountType == account && it.parentId == null }
        if (roots.isEmpty()) null else AccountGroup(account, roots.map(::nodeOf))
    }

    private fun nodeOf(category: DocumentCategory): CategoryNode =
        CategoryNode(category, categories.filter { it.parentId == category.id }.map(::nodeOf))

    private fun ancestorsOf(category: DocumentCategory): List<DocumentCategory> {
        val ancestors = mutableListOf<DocumentCategory>()
        var parentId = category.parentId
        while (parentId != null) {
            val parent = byId[parentId] ?: break
            if (parent in ancestors) break
            ancestors += parent
            parentId = parent.parentId
        }
        return ancestors
    }
}
