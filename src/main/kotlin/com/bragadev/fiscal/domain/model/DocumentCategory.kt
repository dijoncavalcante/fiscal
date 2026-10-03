package com.bragadev.fiscal.domain.model

data class DocumentCategory(
    val id: String,
    val name: String,
    val number: String,
    val accountType: AccountType,
    val parentId: String? = null,
    val namingRule: NamingRule = NamingRule.CATEGORY_NAME,
    /** Palavra usada no nome do arquivo quando difere do nome da categoria ("Despesas" → "Despesa"). */
    val fileBaseName: String? = null,
    /** Categoria opcional (ex.: Outros): mostra a quantidade de arquivos e não conta como "Faltando". */
    val optional: Boolean = false,
) {
    val fileWord: String get() = fileBaseName ?: name

    /**
     * Rótulo da categoria, também usado como nome do arquivo na pasta do mês.
     *
     * - "8" + "Extrato Bancário"         → "8. Extrato Bancário"
     * - "5.1" + "Comprovante Remessa"    → "5.1 Comprovante Remessa"
     * - sem número + "Outros"            → "Outros"
     */
    val label: String
        get() = when {
            number.isBlank() -> name
            number.contains('.') -> "$number $name"
            else -> "$number. $name"
        }
}
