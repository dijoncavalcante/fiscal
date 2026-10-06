package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.CategoryNode
import com.bragadev.fiscal.domain.model.MonthChecklist
import com.bragadev.fiscal.domain.model.MonthReview
import com.bragadev.fiscal.domain.model.ReviewLine
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.YearMonth

/** Monta a conferência do mês a partir da árvore de categorias da conta e do que existe na pasta. */
object MonthReviewBuilder {
    fun build(
        groups: List<AccountGroup>,
        checklist: MonthChecklist,
        month: YearMonth,
        account: AccountType?,
        folder: Path,
        generatedAt: LocalDateTime,
    ): MonthReview {
        val lines = groups.flatMap { group -> flatten(group.nodes, depth = 0) }.map { (node, depth) ->
            val files = checklist.filesFor(node.category.id)
            val issues = files.mapNotNull { name -> checklist.flagFor(name)?.let { name to it } }.toMap()
            ReviewLine(node.category, depth, files, issues)
        }
        val unmatchedIssues = checklist.unmatchedFiles.mapNotNull { name -> checklist.flagFor(name)?.let { name to it } }.toMap()
        return MonthReview(month, account, folder, lines, checklist.unmatchedFiles, unmatchedIssues, generatedAt)
    }

    private fun flatten(nodes: List<CategoryNode>, depth: Int): List<Pair<CategoryNode, Int>> =
        nodes.flatMap { node -> listOf(node to depth) + flatten(node.children, depth + 1) }
}
