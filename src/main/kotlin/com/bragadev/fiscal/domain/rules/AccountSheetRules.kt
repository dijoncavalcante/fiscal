package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import com.bragadev.fiscal.domain.rules.FuzzyText.words
import java.time.YearMonth

/**
 * Conferência da "1. Folha de Contas" das duas contas, pedida pelo usuário em 22/10/2026. O PDF (impressão do JW
 * Hub, ou digitalizado) precisa ter no cabeçalho da primeira página:
 * - o título "FOLHA DE CONTAS (S-26)";
 * - o nome da conta: "Trinta e Um de Março - Manaus AM (68361)" (Congregação) ou "Comissão de Funcionamento do
 *   Salão do Reino" (Manutenção) — o nome completo: a folha da Congregação cita "COMISSÃO DE FUNCIONAMENTO" numa
 *   linha de despesa;
 * - o mês e o ano do mês em edição, logo depois do nome ("agosto de 2026").
 *
 * Folha digitalizada (OCR): o que não foi lido vira "confira à mão", nunca "troque".
 */
object AccountSheetRules {
    private val categories = mapOf(
        AccountType.CONGREGACAO to "congregacao.folha_de_contas",
        AccountType.MANUTENCAO to "manutencao.folha_de_contas",
    )

    /** Nome da conta como aparece no cabeçalho da folha (dados passados pelo usuário). */
    private val headers = mapOf(
        AccountType.CONGREGACAO to "Trinta e Um de Março - Manaus AM (68361)",
        AccountType.MANUTENCAO to "Comissão de Funcionamento do Salão do Reino",
    )

    const val TITLE = "FOLHA DE CONTAS (S-26)"

    /** Categoria "1. Folha de Contas" da conta, ou `null` se a conta não tem folha conferida. */
    fun categoryFor(account: AccountType?): String? = categories[account]

    fun isSheetCategory(categoryId: String?): Boolean = categoryId in categories.values

    fun headerFor(account: AccountType): String? = headers[account]

    /**
     * Resultado e texto da pendência (`null` = folha certa); só o resultado é guardado no banco.
     *
     * @param month mês em edição; `null` = mês não identificado (não confere o mês).
     */
    fun check(content: DocumentContent, account: AccountType, month: YearMonth?): BankStatementRules.Result {
        val header = headers.getValue(account)
        val page = words(content.pageTexts.firstOrNull().orEmpty())
        val fromOcr = 0 in content.ocrPages

        val hasTitle = page.findPhrase(words(TITLE)).any()
        val headerAt = page.findPhrase(words(header)).firstOrNull()
        // Folha da outra conta (caso real: a da Manutenção na pasta de junho/2026 da Congregação).
        val otherAccount = headers.entries.firstOrNull { (other, text) ->
            other != account && page.findPhrase(words(text)).any()
        }?.key.takeIf { headerAt == null }

        val sheetMonth = findMonth(content.pageTexts.firstOrNull().orEmpty(), headers.values)
        val wrongMonth = month != null && sheetMonth != month

        val missing = buildSet {
            if (!hasTitle || headerAt == null) add(ExpenseRequirement.SHEET_HEADER)
            if (wrongMonth) add(ExpenseRequirement.SHEET_MONTH)
        }
        val notes = buildList {
            when {
                otherAccount != null -> add(otherAccountNote(account, otherAccount, fromOcr))
                !hasTitle || headerAt == null -> add(
                    headerNote(
                        listOfNotNull(
                            TITLE.takeIf { !hasTitle }, header.takeIf { headerAt == null },
                        ), fromOcr
                    )
                )
            }
            // Folha da outra conta: o mês dela não importa, a pendência já pede a troca.
            if (wrongMonth && otherAccount == null) add(monthNote(sheetMonth, month, fromOcr))
        }
        val evidence = if (missing.isEmpty()) {
            "$TITLE de $header, ${Months.name(sheetMonth)}"
        } else null
        return BankStatementRules.Result(
            ExpenseCheck(missing, amountEvidence = evidence),
            notes.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR),
        )
    }

    /** Sem OCR das páginas com imagem se a folha já está certa. */
    fun hasEnoughText(content: DocumentContent, account: AccountType, month: YearMonth?): Boolean =
        check(content, account, month).check.isValid

    // ---- Mês ----

    private val monthYear = Regex("""\b([A-Z]{4,9}) DE (\d{4})\b""")

    /**
     * Mês da folha: o "<mês> de <ano>" logo depois do nome da conta no cabeçalho ("... (68361) agosto de 2026");
     * sem o nome (OCR ruim), o primeiro da página. "Trinta e Um de Março - Manaus" não é data (não tem ano).
     */
    internal fun findMonth(firstPage: String, headers: Collection<String>): YearMonth? {
        val page = Months.plain(firstPage)
        val after = headers.map { Months.plain(it) }.firstNotNullOfOrNull { header ->
            page.indexOf(header).takeIf { it >= 0 }?.let { it + header.length }
        } ?: 0
        return monthYear.findAll(page, after).firstNotNullOfOrNull { match ->
            Months.number(match.groupValues[1])?.let { YearMonth.of(match.groupValues[2].toInt(), it) }
        }
    }

    // ---- Pendência ----

    private const val NOTE_SEPARATOR = "; "
    private const val HEADER_PREFIX = "Folha de Contas sem"
    private const val HEADER_OCR_PREFIX = "Cabeçalho da Folha de Contas não lido na foto"
    private const val MONTH_UNREADABLE = "Mês da Folha de Contas não foi lido"

    private fun accountName(account: AccountType) = account.displayName.replaceFirstChar(Char::lowercase)

    private fun otherAccountNote(account: AccountType, other: AccountType, fromOcr: Boolean): String =
        "Folha de Contas da ${accountName(other)} (${headers.getValue(other)}), não da ${accountName(account)}: " +
                if (fromOcr) "confira à mão (foto)" else "troque pela folha certa"

    private fun headerNote(notFound: List<String>, fromOcr: Boolean): String {
        val list = notFound.joinToString(" e ") { "\"$it\"" }
        return if (fromOcr) "$HEADER_OCR_PREFIX (não li $list): confira à mão"
        else "$HEADER_PREFIX $list: troque pela folha certa"
    }

    private fun monthNote(found: YearMonth?, expected: YearMonth?, fromOcr: Boolean): String {
        if (found == null) return "$MONTH_UNREADABLE: confira à mão se é de ${Months.name(expected)}"
        val action = if (fromOcr) "confira à mão (foto)" else "troque pela folha do mês"
        return "Folha de Contas de ${Months.name(found)}, mas o mês em edição é ${Months.name(expected)}: $action"
    }

    /** A pendência foi escrita por esta conferência? As do usuário nunca são mexidas. */
    fun isAutomaticNote(note: String): Boolean =
        note.split(NOTE_SEPARATOR).all { part -> automaticNotes.any { it.matches(part) } }

    private val automaticNotes = listOf(
        Regex("""Folha de Contas da .+ \(.+\), não da .+: (confira à mão \(foto\)|troque pela folha certa)"""),
        Regex("""${Regex.escape(HEADER_PREFIX)} ".+": troque pela folha certa"""),
        Regex("""${Regex.escape(HEADER_OCR_PREFIX)} \(não li ".+"\): confira à mão"""),
        Regex("""${Regex.escape(MONTH_UNREADABLE)}: confira à mão se é de .+"""),
        Regex("""Folha de Contas de .+, mas o mês em edição é .+: (confira à mão \(foto\)|troque pela folha do mês)"""),
        Regex(Regex.escape(ExpenseDocumentRules.UNREADABLE_NOTE)),
    )
}
