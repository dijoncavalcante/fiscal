package com.bragadev.fiscal.domain.model

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Conferência do mês ao concluir: o que existe em cada categoria, o que falta e o que tem pendência.
 * É o conteúdo do relatório em PDF da prestação de contas.
 */
data class MonthReview(
    val month: YearMonth,
    val account: AccountType?,
    val folder: Path,
    val lines: List<ReviewLine>,
    /** PDFs da pasta sem número de categoria no início do nome. */
    val unmatchedFiles: List<String>,
    /** Pendências marcadas em arquivos sem categoria, por nome do arquivo. */
    val unmatchedIssues: Map<String, String>,
    val generatedAt: LocalDateTime,
) {
    val missing: List<ReviewLine> get() = lines.filter { it.isMissing }
    val withIssues: List<ReviewLine> get() = lines.filter { it.issues.isNotEmpty() }
    val requiredCount: Int get() = lines.count { !it.category.optional }
    val doneCount: Int get() = lines.count { !it.category.optional && it.isDone }
    val issueCount: Int get() = lines.sumOf { it.issues.size } + unmatchedIssues.size

    /** Nada faltando e nenhuma pendência: o mês pode ser entregue. */
    val isComplete: Boolean get() = missing.isEmpty() && issueCount == 0
}

/** Uma categoria no relatório, na ordem da árvore ([depth] 0 = categoria principal, 1 = filha, como 5.1). */
data class ReviewLine(
    val category: DocumentCategory,
    val depth: Int,
    val files: List<String>,
    /** Pendências nos arquivos desta categoria: nome do arquivo → nota. */
    val issues: Map<String, String>,
) {
    /** Categoria obrigatória sem nenhum arquivo. Opcionais ("Outros") nunca faltam. */
    val isMissing: Boolean get() = !category.optional && files.isEmpty()
    val isDone: Boolean get() = files.isNotEmpty() && issues.isEmpty()
}
