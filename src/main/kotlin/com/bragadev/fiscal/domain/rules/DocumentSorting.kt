package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.DocumentSort

/** Ordenação e busca da lista de documentos, como no Windows Explorer. */
object DocumentSorting {
    fun apply(documents: List<Document>, sort: DocumentSort, query: String = ""): List<Document> {
        val filtered = if (query.isBlank()) documents else documents.filter { it.name.contains(query.trim(), ignoreCase = true) }
        return when (sort) {
            DocumentSort.MODIFIED_NEWEST_FIRST -> filtered.sortedWith(
                compareByDescending<Document> { it.lastModified }.thenBy { it.name.lowercase() },
            )
            DocumentSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }
}
