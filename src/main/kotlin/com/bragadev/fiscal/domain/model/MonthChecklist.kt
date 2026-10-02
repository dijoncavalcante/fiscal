package com.bragadev.fiscal.domain.model

/**
 * O que já existe na pasta do mês: os PDFs encontrados para cada categoria
 * e os que não puderam ser associados a nenhuma (sem número no início do nome).
 */
data class MonthChecklist(
    val filesByCategory: Map<String, List<String>> = emptyMap(),
    val unmatchedFiles: List<String> = emptyList(),
) {
    fun filesFor(categoryId: String): List<String> = filesByCategory[categoryId].orEmpty()

    fun isPresent(categoryId: String): Boolean = filesFor(categoryId).isNotEmpty()
}
