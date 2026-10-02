package com.bragadev.fiscal.domain.model

/** Nó da árvore de categorias, usado para exibir a estrutura hierárquica. */
data class CategoryNode(
    val category: DocumentCategory,
    val children: List<CategoryNode> = emptyList(),
)

/** Agrupa as categorias raiz de um tipo de conta. */
data class AccountGroup(
    val accountType: AccountType,
    val nodes: List<CategoryNode>,
)
