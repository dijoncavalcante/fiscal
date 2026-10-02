package com.bragadev.fiscal.domain.model

data class DocumentCategory(
    val id: String,
    val name: String,
    val number: String,
    val accountType: AccountType,
    val parentId: String? = null,
    val namingRule: NamingRule = NamingRule.CATEGORY_NAME,
) {
    /**
     * Nome da pasta da categoria.
     *
     * - "8" + "Extrato Bancário"         → "8. Extrato Bancário"
     * - "5.1" + "Comprovante Remessa"    → "5.1 Comprovante Remessa"
     * - sem número + "Outros"            → "Outros"
     */
    val folderName: String
        get() = when {
            number.isBlank() -> name
            number.contains('.') -> "$number $name"
            else -> "$number. $name"
        }
}
