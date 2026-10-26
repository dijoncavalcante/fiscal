package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import com.bragadev.fiscal.domain.rules.FuzzyText.words
import java.math.BigDecimal

/**
 * Conferência do "4. TO-62" da Conta da Congregação (Registro de Transferência de Fundos), pedida pelo usuário em
 * 26/10/2026. O PDF (impressão do JW Hub, quase sempre escaneada depois de assinada) precisa ter:
 * - o título "REGISTRO DE TRANSFERÊNCIA DE FUNDOS (TO-62)";
 * - a congregação "Trinta e Um de Março - Manaus AM (68361)";
 * - a assinatura digital (certificado ou selo gov.br completo) do servo de contas e do responsável das despesas
 *   da Congregação, os dois configurados em Configurações (nenhum nome no código). Assinatura à mão não é
 *   reconhecida (escolha do usuário);
 * - o "TOTAL DE FUNDOS ENVIADOS:" igual ao "Total" do(s) documento(s) de "5. Remessa Betel" do mesmo mês
 *   (impressão do site de donativos: "Total R$975,00"). Sem remessa no mês = pendência.
 *
 * TO-62 escaneado (OCR): o que não foi lido ou não bate vira "confira à mão", nunca "troque".
 */
object TransferRecordRules {
    const val CATEGORY = "congregacao.to62"
    const val REMITTANCE_CATEGORY = "congregacao.remessa_betel"

    /** Como aparece no nome da categoria da remessa, para a pendência. */
    private const val REMITTANCE_LABEL = "\"5. Remessa Betel\""

    const val TITLE = "REGISTRO DE TRANSFERÊNCIA DE FUNDOS (TO-62)"
    const val CONGREGATION = "Trinta e Um de Março - Manaus AM (68361)"

    fun categoryFor(account: AccountType?): String? = CATEGORY.takeIf { account == AccountType.CONGREGACAO }

    fun remittanceCategoryFor(account: AccountType?): String? =
        REMITTANCE_CATEGORY.takeIf { account == AccountType.CONGREGACAO }

    fun isTransferRecordCategory(categoryId: String?): Boolean = categoryId == CATEGORY

    /**
     * Quem precisa assinar: o servo de contas e o responsável das despesas da Congregação (`null` = não
     * configurado).
     */
    data class Requirements(val accountsServant: String?, val expenseSigner: String?) {
        val key: String get() = "${accountsServant.orEmpty()}|${expenseSigner.orEmpty()}"
    }

    /**
     * Os documentos de "5. Remessa Betel" do mês.
     *
     * @param files nomes dos arquivos (vazio = nenhum documento na categoria).
     * @param total soma do "Total" de cada um; `null` = algum não foi lido.
     */
    data class Remittance(val files: List<String>, val total: BigDecimal?)

    /** Resultado e texto da pendência (`null` = TO-62 certo); só o resultado é guardado no banco. */
    fun check(content: DocumentContent, requirements: Requirements, remittance: Remittance): BankStatementRules.Result {
        val firstPage = content.pageTexts.firstOrNull().orEmpty()
        val page = words(firstPage)
        val fromOcr = 0 in content.ocrPages

        val hasTitle = page.findPhrase(words(TITLE)).any()
        val hasCongregation = page.findPhrase(words(CONGREGATION)).any()

        val signers = listOf(
            SERVANT to requirements.accountsServant,
            EXPENSE_SIGNER to requirements.expenseSigner,
        )
        val notConfigured = signers.filter { it.second == null }.map { it.first }
        val unsigned = signers.mapNotNull { (_, name) ->
            name?.takeIf { !ExpenseDocumentRules.isSignedBy(content, it) }
        }

        val sent = content.pageTexts.firstNotNullOfOrNull(::fundsSent)
        val totalProblem = totalNote(sent, remittance, fromOcr = fromOcr || content.ocrPages.isNotEmpty())

        val missing = buildSet {
            if (!hasTitle || !hasCongregation) add(ExpenseRequirement.TRANSFER_HEADER)
            if (notConfigured.isNotEmpty() || unsigned.isNotEmpty()) add(ExpenseRequirement.SIGNATURE)
            if (totalProblem != null) add(ExpenseRequirement.TRANSFER_TOTAL)
        }
        val notes = buildList {
            if (!hasTitle) add(headerNote("\"$TITLE\"", fromOcr))
            if (!hasCongregation) add(headerNote("a congregação \"$CONGREGATION\"", fromOcr))
            if (unsigned.isNotEmpty()) add("$UNSIGNED_PREFIX de ${unsigned.joinToString(" e ")}")
            notConfigured.forEach { add("$UNSIGNED_PREFIX $it $NOT_CONFIGURED_SUFFIX") }
            totalProblem?.let(::add)
        }
        val evidence = if (missing.isEmpty() && sent != null) {
            "TOTAL DE FUNDOS ENVIADOS R$ ${Money.format(sent)}, igual ao da Remessa Betel; assinado por " +
                    signers.mapNotNull { it.second }.joinToString(" e ")
        } else null
        return BankStatementRules.Result(
            ExpenseCheck(missing, amountEvidence = evidence),
            notes.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR),
        )
    }

    /** Sem OCR das páginas com imagem se o TO-62 já está certo. */
    fun hasEnoughText(content: DocumentContent, requirements: Requirements, remittance: Remittance): Boolean =
        check(content, requirements, remittance).check.isValid

    // ---- Valores ----

    /**
     * Valor com o OCR às vezes sem a vírgula dos centavos ("1.13225" = 1.132,25): só depois de um milhar com
     * ponto, para "97500" não virar 975,00.
     */
    private const val AMOUNT = """(${Money.PATTERN}|(\d{1,3}(?:\.\d{3})+)(\d{2})(?!\d))"""

    private val fundsSentLabel =
        Regex("""T[O0]TAL DE FUND[O0]S ENVIAD[O0]S\W{0,6}$AMOUNT""")

    /** "R$975,00" logo depois de "Total" (o primeiro da página); o OCR lê "R$" como "RS". */
    private val remittanceTotalLabel = Regex("""\bT[O0]TAL:? ?R ?[${'$'}S] ?$AMOUNT""")

    /** "TOTAL DE FUNDOS ENVIADOS: 975,00" do TO-62; `null` se não foi lido. */
    internal fun fundsSent(pageText: String): BigDecimal? = fundsSentLabel.find(Months.plain(pageText))?.let(::amountOf)

    /** "Total R$975,00" da Remessa Betel; `null` se não foi lido. */
    fun remittanceTotal(content: DocumentContent): BigDecimal? =
        content.pageTexts.firstNotNullOfOrNull { remittanceTotalLabel.find(Months.plain(it))?.let(::amountOf) }

    private fun amountOf(match: MatchResult): BigDecimal {
        val g = match.groupValues
        return if (g[2].isNotEmpty()) Money.amount(g[2], g[3]) else Money.amount(g[4], g[5])
    }

    // ---- Pendência ----

    private const val NOTE_SEPARATOR = "; "
    private const val SERVANT = "do servo de contas"
    private const val EXPENSE_SIGNER = "do responsável das despesas da Congregação"
    private const val UNSIGNED_PREFIX = "Falta assinatura digital (gov.br)"
    private const val NOT_CONFIGURED_SUFFIX =
        "(configure em Configurações → Assinaturas das despesas e extratos)"
    private const val SENT = "TOTAL DE FUNDOS ENVIADOS do TO-62"

    private fun headerNote(what: String, fromOcr: Boolean): String =
        if (fromOcr) "TO-62 sem $what na foto: confira à mão" else "TO-62 sem $what: troque pelo TO-62 certo"

    private fun totalNote(sent: BigDecimal?, remittance: Remittance, fromOcr: Boolean): String? {
        val total = remittance.total
        return when {
            remittance.files.isEmpty() ->
                (sent?.let { "$SENT (R$ ${Money.format(it)})" } ?: SENT) +
                        " não pôde ser conferido: $REMITTANCE_LABEL não tem nenhum documento neste mês"

            sent == null -> "$SENT não foi lido: confira à mão se é igual ao Total da Remessa Betel" +
                    (total?.let { " (R$ ${Money.format(it)})" } ?: "")

            total == null -> "Total da Remessa Betel não foi lido: confira à mão se é igual ao $SENT " +
                    "(R$ ${Money.format(sent)})"

            sent.compareTo(total) != 0 -> "$SENT (R$ ${Money.format(sent)}) é diferente do Total da Remessa " +
                    "Betel (R$ ${Money.format(total)}): " +
                    if (fromOcr) "confira à mão (foto)" else "corrija o documento errado"

            else -> null
        }
    }

    /** A pendência foi escrita por esta conferência? As do usuário nunca são mexidas. */
    fun isAutomaticNote(note: String): Boolean =
        note.split(NOTE_SEPARATOR).all { part -> automaticNotes.any { it.matches(part) } }

    private val automaticNotes = listOf(
        Regex("""TO-62 sem .+ na foto: confira à mão"""),
        Regex("""TO-62 sem .+: troque pelo TO-62 certo"""),
        Regex("""${Regex.escape(UNSIGNED_PREFIX)} .+"""),
        Regex("""${Regex.escape(SENT)}( \(R\$ [\d.,]+\))? não pôde ser conferido: .+"""),
        Regex("""${Regex.escape(SENT)} não foi lido: confira à mão .+"""),
        Regex("""Total da Remessa Betel não foi lido: confira à mão .+"""),
        Regex("""${Regex.escape(SENT)} \(R\$ [\d.,]+\) é diferente do Total da Remessa Betel .+"""),
        Regex(Regex.escape(ExpenseDocumentRules.UNREADABLE_NOTE)),
    )
}
