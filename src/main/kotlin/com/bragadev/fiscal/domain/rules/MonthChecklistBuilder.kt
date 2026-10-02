package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.MonthChecklist
import com.bragadev.fiscal.domain.model.NamingRule

/**
 * Associa os PDFs da pasta do mês às categorias pelo número no início do nome do arquivo,
 * porque o restante do nome varia nas pastas reais:
 *
 * - "8. Extrato Bancário.pdf", "7. Relatório Mensal (S-30).pdf" → categorias 8 e 7
 * - "5.1 Comprovante Remessa.pdf" → categoria 5.1 (não a 5)
 * - "3.1 Despesa - ônibus.pdf" → não existe categoria 3.1, então conta como mais uma Despesa (3)
 * - "1. Outros.pdf" → "Outros" (verificado antes do número, para não cair na categoria 1)
 * - "Recibos agosto.pdf" → sem número: fica em [MonthChecklist.unmatchedFiles]
 *
 * [categories] deve conter só as categorias da conta da pasta, pois os números se repetem entre contas.
 */
object MonthChecklistBuilder {
    private val leadingNumber = Regex("""^(\d+(?:\.\d+)?)(?=[.\s\-_])""")

    fun build(categories: List<DocumentCategory>, pdfNames: Collection<String>): MonthChecklist {
        val byNumber = categories.filter { it.number.isNotBlank() }.associateBy { it.number }
        val sequential = categories.filter { it.namingRule == NamingRule.SEQUENTIAL }
        val matched = linkedMapOf<String, MutableList<String>>()
        val unmatched = mutableListOf<String>()

        pdfNames.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { name ->
            val category = sequential.firstOrNull { SequentialNaming.matches(it.name, name) }
                ?: categoryByNumber(name, byNumber)
            if (category == null) unmatched += name else matched.getOrPut(category.id) { mutableListOf() } += name
        }
        return MonthChecklist(matched, unmatched)
    }

    private fun categoryByNumber(fileName: String, byNumber: Map<String, DocumentCategory>): DocumentCategory? {
        val number = leadingNumber.find(fileName)?.groupValues?.get(1) ?: return null
        return byNumber[number] ?: byNumber[number.substringBefore('.')]
    }
}
