package com.bragadev.fiscal.domain.model

/**
 * O que já existe na pasta do mês: os PDFs encontrados para cada categoria
 * e os que não puderam ser associados a nenhuma (sem número no início do nome).
 */
data class MonthChecklist(
    val filesByCategory: Map<String, List<String>> = emptyMap(),
    val unmatchedFiles: List<String> = emptyList(),
    /** Pendências marcadas pelo usuário, por nome de arquivo em minúsculas. */
    val flags: Map<String, String> = emptyMap(),
) {
    fun filesFor(categoryId: String): List<String> = filesByCategory[categoryId].orEmpty()

    fun isPresent(categoryId: String): Boolean = filesFor(categoryId).isNotEmpty()

    fun flagFor(fileName: String): String? = flags[fileName.lowercase()]

    /** Alguma pendência entre os arquivos da categoria: ela não conta como resolvida. */
    fun hasPendingIssue(categoryId: String): Boolean = filesFor(categoryId).any { flagFor(it) != null }
}
