package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import com.bragadev.fiscal.domain.rules.FuzzyText.words
import java.time.YearMonth

/**
 * Conferência do "Relatório Mensal" das duas contas ("7." na Congregação, "4." na Manutenção), pedida pelo usuário
 * em 26/10/2026. A primeira página do PDF (impressão do JW Hub, ou digitalizado) precisa ter:
 * - o título do formulário da conta: "RELATÓRIO MENSAL DE CONTAS DA CONGREGAÇÃO (S-30)" ou "RELATÓRIO MENSAL DE
 *   CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO REINO (S-44)" (no PDF o título da S-44 quebra a linha);
 * - o mês e o ano do mês em edição ("setembro de 2026"), logo depois das instruções (na S-30, depois do nome da
 *   congregação: "Trinta e Um de Março - Manaus AM (68361) setembro de 2026" — "Março - Manaus" não é data).
 *
 * Relatório digitalizado (OCR): o que não foi lido vira "confira à mão", nunca "troque".
 */
object MonthlyReportRules {
    private val categories = mapOf(
        AccountType.CONGREGACAO to "congregacao.relatorio_mensal",
        AccountType.MANUTENCAO to "manutencao.relatorio_mensal",
    )

    /** Título do formulário de cada conta (dados passados pelo usuário). */
    private val titles = mapOf(
        AccountType.CONGREGACAO to "RELATÓRIO MENSAL DE CONTAS DA CONGREGAÇÃO (S-30)",
        AccountType.MANUTENCAO to "RELATÓRIO MENSAL DE CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO REINO (S-44)",
    )

    /** Categoria "Relatório Mensal" da conta, ou `null` se a conta não tem relatório conferido. */
    fun categoryFor(account: AccountType?): String? = categories[account]

    fun isReportCategory(categoryId: String?): Boolean = categoryId in categories.values

    fun titleFor(account: AccountType): String? = titles[account]

    /**
     * Resultado e texto da pendência (`null` = relatório certo); só o resultado é guardado no banco.
     *
     * @param month mês em edição; `null` = mês não identificado (não confere o mês).
     */
    fun check(content: DocumentContent, account: AccountType, month: YearMonth?): BankStatementRules.Result {
        val title = titles.getValue(account)
        val firstPage = content.pageTexts.firstOrNull().orEmpty()
        val page = words(firstPage)
        val fromOcr = 0 in content.ocrPages

        val hasTitle = page.findPhrase(words(title)).any()
        // Relatório da outra conta (S-30 na pasta da Manutenção ou S-44 na da Congregação).
        val otherAccount = titles.entries.firstOrNull { (other, text) ->
            other != account && page.findPhrase(words(text)).any()
        }?.key.takeIf { !hasTitle }

        val reportMonth = findMonth(firstPage)
        val wrongMonth = month != null && reportMonth != month

        val missing = buildSet {
            if (!hasTitle) add(ExpenseRequirement.REPORT_TITLE)
            if (wrongMonth) add(ExpenseRequirement.REPORT_MONTH)
        }
        val notes = buildList {
            when {
                otherAccount != null -> add(otherAccountNote(account, otherAccount, fromOcr))
                !hasTitle -> add(titleNote(title, fromOcr))
            }
            // Relatório da outra conta: o mês dele não importa, a pendência já pede a troca.
            if (wrongMonth && otherAccount == null) add(monthNote(reportMonth, month, fromOcr))
        }
        val evidence = if (missing.isEmpty()) "$title, ${Months.name(reportMonth)}" else null
        return BankStatementRules.Result(
            ExpenseCheck(missing, amountEvidence = evidence),
            notes.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR),
        )
    }

    /** Sem OCR das páginas com imagem se o relatório já está certo. */
    fun hasEnoughText(content: DocumentContent, account: AccountType, month: YearMonth?): Boolean =
        check(content, account, month).check.isValid

    // ---- Mês ----

    private val monthYear = Regex("""\b([A-Z]{4,9}) DE (\d{4})\b""")

    /**
     * Mês do relatório: o primeiro "<mês> de <ano>" da primeira página. As instruções não têm nenhum, a data da
     * impressão do navegador é só em números ("05/12/2025") e "Trinta e Um de Março - Manaus" não tem ano.
     */
    internal fun findMonth(firstPage: String): YearMonth? =
        monthYear.findAll(Months.plain(firstPage)).firstNotNullOfOrNull { match ->
            Months.number(match.groupValues[1])?.let { YearMonth.of(match.groupValues[2].toInt(), it) }
        }

    // ---- Pendência ----

    private const val NOTE_SEPARATOR = "; "
    private const val TITLE_PREFIX = "Relatório Mensal sem"
    private const val TITLE_OCR_PREFIX = "Título do Relatório Mensal não lido na foto"
    private const val MONTH_UNREADABLE = "Mês do Relatório Mensal não foi lido"

    private fun accountName(account: AccountType) = account.displayName.replaceFirstChar(Char::lowercase)

    private fun formOf(account: AccountType) = titles.getValue(account).substringAfterLast("(").substringBefore(")")

    private fun otherAccountNote(account: AccountType, other: AccountType, fromOcr: Boolean): String =
        "Relatório Mensal da ${accountName(other)} (${formOf(other)}), não da ${accountName(account)} " +
                "(${formOf(account)}): " + if (fromOcr) "confira à mão (foto)" else "troque pelo relatório certo"

    private fun titleNote(title: String, fromOcr: Boolean): String =
        if (fromOcr) "$TITLE_OCR_PREFIX (não li \"$title\"): confira à mão"
        else "$TITLE_PREFIX \"$title\": troque pelo relatório certo"

    private fun monthNote(found: YearMonth?, expected: YearMonth?, fromOcr: Boolean): String {
        if (found == null) return "$MONTH_UNREADABLE: confira à mão se é de ${Months.name(expected)}"
        val action = if (fromOcr) "confira à mão (foto)" else "troque pelo relatório do mês"
        return "Relatório Mensal de ${Months.name(found)}, mas o mês em edição é ${Months.name(expected)}: $action"
    }

    /** A pendência foi escrita por esta conferência? As do usuário nunca são mexidas. */
    fun isAutomaticNote(note: String): Boolean =
        note.split(NOTE_SEPARATOR).all { part -> automaticNotes.any { it.matches(part) } }

    private val automaticNotes = listOf(
        Regex("""Relatório Mensal da .+ \(.+\), não da .+ \(.+\): (confira à mão \(foto\)|troque pelo relatório certo)"""),
        Regex("""${Regex.escape(TITLE_PREFIX)} ".+": troque pelo relatório certo"""),
        Regex("""${Regex.escape(TITLE_OCR_PREFIX)} \(não li ".+"\): confira à mão"""),
        Regex("""${Regex.escape(MONTH_UNREADABLE)}: confira à mão se é de .+"""),
        Regex("""Relatório Mensal de .+, mas o mês em edição é .+: (confira à mão \(foto\)|troque pelo relatório do mês)"""),
        Regex(Regex.escape(ExpenseDocumentRules.UNREADABLE_NOTE)),
    )
}
