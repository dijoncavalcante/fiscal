package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.rules.ExpenseDocumentRules.MIN_CLAIM_CONFIDENCE
import com.bragadev.fiscal.domain.rules.ExpenseDocumentRules.MIN_MATCH_CONFIDENCE
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import com.bragadev.fiscal.domain.rules.FuzzyText.words
import java.math.BigDecimal
import java.text.Normalizer
import java.time.YearMonth

/**
 * Conferência das despesas ("3. Despesas") das contas da Congregação e da Manutenção. Cada PDF precisa ter:
 * - a assinatura digital (gov.br) do responsável da conta, configurado em Configurações ([ExpenseRequirements]);
 * - o comprovante do Pix/TED;
 * - o cupom, a nota fiscal ou o recibo, com o valor pago no comprovante igual ao total — exceto nas despesas
 *   isentas da Congregação ([ExpenseExemptionRules], pelo nome do arquivo), que não exigem cupom nem conferem valor.
 *
 * Trabalha só com o que já foi lido do PDF: texto (PDF ou OCR), palavras com a posição na página e os nomes
 * dos certificados das assinaturas. O texto é normalizado (sem acentos, maiúsculas, só letras e números) e os
 * termos são procurados palavra por palavra, tolerando poucos erros de letra em palavras longas — o OCR de foto
 * de cupom troca letras com frequência ("Nata Fecal" para "Nota Fiscal").
 */
object ExpenseDocumentRules {
    /** Categoria "3. Despesas" da conta, ou `null` se a conta não tem despesas conferidas. */
    fun categoryFor(account: AccountType?): String? = when (account) {
        AccountType.CONGREGACAO -> "congregacao.despesas"
        AccountType.MANUTENCAO -> "manutencao.despesas"
        else -> null
    }

    /** Só a Congregação tem despesas isentas de cupom. */
    fun hasExemptions(account: AccountType?): Boolean = account == AccountType.CONGREGACAO

    /** Cupom fiscal, nota fiscal (DANFE, NFC-e) ou conta de consumo (água, luz, internet). */
    private val fiscalTerms = listOf(
        "CUPOM FISCAL", "CUPOM", "NFC-e", "NFCe", "NF-e", "NFe", "CF-e", "NOTA FISCAL", "DANFE",
        "DOCUMENTO AUXILIAR", "CHAVE DE ACESSO", "CONSULTA DE AUTENTICIDADE", "COMPROVANTE DE VENDA", "FATURA",
        "KWH", "LEITURA ATUAL", "LEITURA ANTERIOR", "ILUMINACAO PUBLICA",
    ).map(::words)

    private val receipt = words("RECIBO")
    private val budget = words("ORCAMENTO")

    private val comprovante = words("COMPROVANTE")
    private val paymentWords = listOf("PIX", "TED", "TRANSFERENCIA").map(::words)
    private val paymentPhrases = listOf("ID DA TRANSACAO", "ID DA TRANSFERENCIA").map(::words)
    private val signatureStamp = words("ASSINADO DIGITALMENTE")
    private val stampValidationSite = words("VALIDAR")

    /** Identificador de uma transação Pix (E2E): "E" + 31 letras/números, ex.: E60746948202609191355M3943eq0hGo. */
    private val pixEndToEndId = Regex("""^E\d{20}[A-Z0-9]{11}$""")

    /** Quantas palavras depois de "assinado digitalmente" o nome pode aparecer no selo. */
    private const val STAMP_WINDOW = 12

    /**
     * Quantas palavras depois de "assinado digitalmente" pode estar o "Verifique em https://validar.iti.gov.br"
     * (o OCR mistura no meio a data e o que estiver ao lado do selo: "VALOR R$ 82,00").
     */
    private const val STAMP_SITE_WINDOW = 40

    /**
     * Confiança mínima do OCR (0 a 100) para um total lido na foto valer. Para **bater** com o comprovante basta
     * uma confiança média: um erro de leitura que dê exatamente o valor do Pix é quase impossível. Para **afirmar**
     * um valor diferente na pendência, a leitura precisa ser boa.
     */
    private const val MIN_MATCH_CONFIDENCE = 40f
    private const val MIN_CLAIM_CONFIDENCE = 70f

    fun check(content: DocumentContent, requirements: ExpenseRequirements): ExpenseCheck {
        val pages = content.pageTexts.map(::words)
        val paymentPages = pages.indices.filter { hasPaymentProof(pages[it]) }.toSet()
        val fiscalPages = pages.indices.filter { hasFiscalDocument(pages[it], isPaymentProof = it in paymentPages) }
        val signer = requirements.signerName?.takeIf { it.isNotBlank() }
        val missing = buildSet {
            if (fiscalPages.isEmpty() && requirements.exemptBy == null) add(ExpenseRequirement.FISCAL_DOCUMENT)
            if (paymentPages.isEmpty()) add(ExpenseRequirement.PAYMENT_PROOF)
            if (signer == null || !isSigned(content.signers, pages, words(signer))) add(ExpenseRequirement.SIGNATURE)
        }
        val amount = requirements.exemptBy?.let { AmountCheck(evidence = exemptionEvidence(it)) }
            ?: amountCheck(content, paymentPages, fiscalPages)
        val dateProblem = requirements.month?.let { paymentDateProblem(content, paymentPages, it) }
        return ExpenseCheck(
            if (dateProblem != null) missing + ExpenseRequirement.PAYMENT_MONTH else missing,
            amountProblem = amount.problem,
            amountEvidence = amount.evidence,
            signerName = signer,
            paymentDate = dateProblem,
        )
    }

    /**
     * Cada comprovante Pix/TED do arquivo precisa ter a data da transferência no mês em edição ([TransferDates]).
     * Página de comprovante sem data lida não conta se outra tem; nenhuma lida = conferir à mão. Sem comprovante, a
     * pendência "Falta comprovante" já diz o que fazer.
     */
    private fun paymentDateProblem(
        content: DocumentContent,
        paymentPages: Set<Int>,
        month: YearMonth
    ): PaymentDateProblem? {
        if (paymentPages.isEmpty()) return null
        val dates = paymentPages.sorted().mapNotNull { page ->
            TransferDates.find(content.pageTexts[page])?.let { it to (page in content.ocrPages || it.fromPixId) }
        }
        if (dates.isEmpty()) return PaymentDateProblem(month, found = null, uncertain = true)
        val (outside, uncertain) = dates.firstOrNull { YearMonth.from(it.first.date) != month } ?: return null
        return PaymentDateProblem(month, outside.date, uncertain)
    }

    /**
     * Já dá para dar a resposta? Comprovante achado e, quando o cupom é exigido, cupom/nota achados e o valor
     * resolvido (bateu, ou é diferente num PDF de texto). Enquanto não, o leitor continua: faz OCR das outras
     * páginas com imagem e novas leituras das fotos.
     */
    fun hasEnoughText(content: DocumentContent, requirements: ExpenseRequirements): Boolean {
        val result = check(content, requirements)
        return ExpenseRequirement.FISCAL_DOCUMENT !in result.missing &&
                ExpenseRequirement.PAYMENT_PROOF !in result.missing &&
                (result.amountProblem == null || result.amountProblem.kind == AmountProblemKind.MISMATCH)
    }

    private fun exemptionEvidence(term: String) = "Sem cupom: despesa isenta (\"$term\" no nome do arquivo)"

    private class AmountCheck(val problem: AmountProblem? = null, val evidence: String? = null)

    /**
     * Valor pago × total do cupom/nota. O valor pago é o "Valor:" de cada página de comprovante (somado, se
     * houver mais de um). Ele precisa ser igual a um total do cupom/nota ou à soma dos totais de cupons/notas
     * diferentes (várias compras pagas num Pix só). Totais vêm de dois jeitos:
     * - no texto: o valor logo depois de "Total", "Valor total", "Valor a pagar"…;
     * - pela posição ([TableTotalFinder]): o valor à direita do título ou embaixo dele, na tabela.
     * Outros valores da página (preço de item, "Valor bruto", "SubTotal") não contam.
     *
     * Segurança: um total de foto só vale com a confiança do OCR ([MIN_MATCH_CONFIDENCE]) ou com a conta da
     * tabela fechando; uma diferença só é afirmada com leitura boa ([MIN_CLAIM_CONFIDENCE]) e, em foto, a
     * pendência pede para conferir à mão. Valor não lido = pendência para conferir à mão.
     * Sem comprovante ou sem cupom/nota não há o que comparar: a pendência "Falta …" já diz isso.
     */
    private fun amountCheck(content: DocumentContent, paymentPages: Set<Int>, fiscalPages: List<Int>): AmountCheck {
        if (paymentPages.isEmpty() || fiscalPages.isEmpty()) return AmountCheck()
        val raw = content.pageTexts.map(::plain)
        // Nova leitura só para comprovante sozinho na página: num cupom que cita o Pix, o "Valor" seria do cupom.
        val payments = paymentPages.sorted().mapNotNull { page ->
            paidAmount(raw[page]) ?: paidInLayouts(content, page).takeIf { page !in fiscalPages }
        }
        if (payments.isEmpty()) return AmountCheck(AmountProblem(AmountProblemKind.PAYMENT_UNREADABLE))
        val paid = payments.reduce(BigDecimal::add)

        val candidates = fiscalPages.map { page -> totalCandidates(content, page, raw[page], page in paymentPages) }
        val search = CombinationSearch(candidates.map { page -> page.filter(::canMatch) })
        // A soma dos comprovantes bate, ou cada comprovante bate com um cupom/nota.
        val match = search.find(Money.cents(paid))
            ?: payments.takeIf { it.size > 1 }?.map { search.find(Money.cents(it)) }
                ?.takeIf { each -> each.all { it != null } }?.flatMap { it.orEmpty() }
        if (match != null) return AmountCheck(evidence = describeTotals(match))
        if (search.gaveUp) return AmountCheck(AmountProblem(AmountProblemKind.TOTAL_UNREADABLE, paid))

        val reliable = candidates.map { page -> page.filter(::canClaim) }
        if (reliable.all { it.isEmpty() }) return AmountCheck(AmountProblem(AmountProblemKind.TOTAL_UNREADABLE, paid))
        val fiscal = reliable.mapNotNull(::mainTotal).map { it.value }.reduce(BigDecimal::add)
        val fromPhoto = (fiscalPages + paymentPages).any { it in content.ocrPages }
        val kind = if (fromPhoto) AmountProblemKind.UNCERTAIN_READING else AmountProblemKind.MISMATCH
        return AmountCheck(AmountProblem(kind, paid, fiscal))
    }

    /**
     * Totais de uma página de cupom/nota: os do texto e os achados pela posição em cada leitura da página.
     * Cupom e comprovante na mesma página: o "Valor:" do Pix é tirado do texto antes, e a posição não é usada
     * (o "Valor total" do comprovante seria confundido com o do cupom).
     */
    private fun totalCandidates(
        content: DocumentContent,
        page: Int,
        text: String,
        isPaymentPage: Boolean
    ): List<TotalCandidate> {
        val fromOcr = page in content.ocrPages
        val inText = totalMatches(if (isPaymentPage) text.replace(paidLabel, " ") else text).map { (label, value) ->
            TotalCandidate(
                value, page, label, TotalPlace.TEXT, fromOcr, isPayable = Regex(payableLabel).matches(label),
            )
        }.toList()
        if (isPaymentPage) return inText
        return inText + content.layouts.filter { it.page == page }.flatMap(TableTotalFinder::find)
    }

    private fun canMatch(candidate: TotalCandidate): Boolean =
        !candidate.fromOcr || candidate.confirmedBySum || candidate.confidence >= MIN_MATCH_CONFIDENCE

    private fun canClaim(candidate: TotalCandidate): Boolean =
        !candidate.fromOcr || candidate.confirmedBySum || candidate.confidence >= MIN_CLAIM_CONFIDENCE

    /** Melhor total de uma página: conta da tabela conferida, "valor a pagar", mais confiável; empate = o último. */
    private val preference = compareBy<IndexedValue<TotalCandidate>>(
        { it.value.confirmedBySum },
        { it.value.isPayable },
        { !it.value.fromOcr },
        { it.value.confidence },
        { it.index },
    )

    private fun mainTotal(candidates: List<TotalCandidate>): TotalCandidate? =
        candidates.withIndex().maxWithOrNull(preference)?.value

    /**
     * Procura uma combinação de no máximo um total por página que some o valor pago. Desiste (e não afirma
     * nada) se as combinações passarem do limite.
     */
    private class CombinationSearch(pages: List<List<TotalCandidate>>) {
        // Um candidato por valor em cada página, o melhor primeiro (é ele que aparece em "de onde veio").
        private val pages = pages.map { page ->
            page.withIndex().sortedWith(preference.reversed()).map { it.value }.distinctBy { Money.cents(it.value) }
        }
        var gaveUp = false
            private set
        private var steps = 0

        fun find(target: Long): List<TotalCandidate>? {
            steps = 0
            return search(0, target)
        }

        private fun search(index: Int, remaining: Long): List<TotalCandidate>? {
            if (remaining == 0L) return emptyList()
            if (index == pages.size || remaining < 0) return null
            if (++steps > MAX_STEPS) {
                gaveUp = true
                return null
            }
            for (candidate in pages[index]) {
                search(index + 1, remaining - Money.cents(candidate.value))?.let { return listOf(candidate) + it }
            }
            return search(index + 1, remaining)
        }

        private companion object {
            const val MAX_STEPS = 200_000
        }
    }

    /** "Valor conferido: R$ 50,00 abaixo de "TOTAL (R$)", página 1 (foto, conta da tabela confere)". */
    private fun describeTotals(totals: List<TotalCandidate>): String = "Valor conferido: " + describeEach(totals)

    private fun describeEach(totals: List<TotalCandidate>): String {
        val each = totals.map { total ->
            val where = if (total.place == TotalPlace.BELOW) "abaixo de" else "em"
            val source = buildList {
                add(if (total.fromOcr) "foto" else "texto do PDF")
                if (total.confirmedBySum) add("conta da tabela confere")
            }.joinToString(", ")
            "R$ ${Money.format(total.value)} $where \"${total.label}\", página ${total.page + 1} ($source)"
        }
        return if (each.size == 1) each.single() else "Soma de ${each.size} totais: " + each.joinToString("; ")
    }

    private val paidLabel =
        Regex("""(?:VALOR(?: DO PAGAMENTO| PAGO| DA TRANSFERENCIA| TRANSFERIDO| TOTAL)?|PIX ENVIADO)\s*:?\s*(?:R\$)?\s*${Money.PATTERN}""")

    /** "Valor a pagar", também com a letra trocada ou cortada pelo OCR ("VALOR & PAGAR", "Valor a Pag"). */
    private const val payableLabel = """VALOR \S PAG\w*|TOTAL A PAGAR|VALOR PAGO"""
    private val totalLabel = Regex(
        """($payableLabel|VALOR TOTAL(?: DA NOTA)?|VALOR LIQUIDO|VALOR (?:DO |DA )?(?:PEDIDO|VENDA|DOCUMENTO|COBRADO)|TOTAL)""" +
                """\D{0,25}?${Money.PATTERN}""",
    )

    private fun paidAmount(page: String): BigDecimal? =
        paidLabel.findAll(page).map { Money.amount(it.groupValues[1], it.groupValues[2]) }
            .firstOrNull { it.signum() > 0 }

    /** Comprovante fotografado cujo valor o primeiro OCR embaralhou: tenta nas novas leituras da página. */
    private fun paidInLayouts(content: DocumentContent, page: Int): BigDecimal? =
        content.layouts.filter { it.page == page }
            .firstNotNullOfOrNull { paidAmount(plain(TableTotalFinder.lineTexts(it).joinToString(" "))) }

    private fun totalMatches(page: String): Sequence<Pair<String, BigDecimal>> =
        totalLabel.findAll(page).map { it.groupValues[1] to Money.amount(it.groupValues[2], it.groupValues[3]) }
            .filter { it.second.signum() > 0 }

    /**
     * Texto sem acentos e em maiúsculas, mas com a pontuação (precisa dela para ler valores). Logo depois de
     * "R$" o OCR às vezes perde a vírgula ("R$ 166 92"): ali, e só ali, o espaço vira vírgula.
     */
    private fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "").uppercase()
            .replace(Regex("""\s+"""), " ")
            .replace(lostComma, "$1 $2,$3")

    private val lostComma = Regex("""\b(R\$|RS) ?(\d{1,6}) (\d{2})(?![\d.,])""")

    /**
     * Cupom, nota fiscal ou recibo. "Recibo" não vale numa página de orçamento nem no próprio comprovante do
     * Pix/TED (alguns bancos chamam o comprovante de recibo).
     */
    private fun hasFiscalDocument(page: List<String>, isPaymentProof: Boolean): Boolean =
        fiscalTerms.any { page.findPhrase(it).any() } ||
                (!isPaymentProof && page.findPhrase(receipt).any() && page.findPhrase(budget).none())

    /**
     * Comprovante Pix/TED: "comprovante" junto de Pix, TED ou transferência na mesma página, ou o
     * identificador da transação. Uma fatura que só diz "pague com Pix" não conta.
     */
    internal fun hasPaymentProof(page: List<String>): Boolean =
        (page.findPhrase(comprovante).any() && paymentWords.any { page.findPhrase(it).any() }) ||
                paymentPhrases.any { page.findPhrase(it).any() } || page.any(pixEndToEndId::matches)

    /**
     * Assinado pelo responsável: o certificado de alguma assinatura digital tem o nome dele (o mais seguro), ou o
     * texto traz o selo do gov.br inteiro — "Documento assinado digitalmente", o nome e "Verifique em
     * https://validar.iti.gov.br"; a data do selo é ignorada. Só o nome solto no texto não basta: ele aparece,
     * por exemplo, como recebedor do Pix.
     */
    internal fun isSignedBy(content: DocumentContent, signerName: String): Boolean =
        isSigned(content.signers, content.pageTexts.map(::words), words(signerName))

    private fun isSigned(signers: List<String>, pages: List<List<String>>, signerWords: List<String>): Boolean =
        signers.any { words(it).findPhrase(signerWords).any() } ||
                pages.any { page ->
                    page.findPhrase(signatureStamp).any { start ->
                        val from = start + signatureStamp.size
                        val nameArea = page.subList(from, minOf(page.size, from + STAMP_WINDOW))
                        val siteArea = page.subList(from, minOf(page.size, from + STAMP_SITE_WINDOW))
                        nameArea.findPhrase(signerWords).any() && siteArea.findPhrase(stampValidationSite).any()
                    }
                }

    /** Pendência gravada para o arquivo, em poucas palavras; `null` se a despesa está completa. */
    fun issueNote(check: ExpenseCheck): String? {
        if (check.unreadable) return UNREADABLE_NOTE
        val parts = listOfNotNull(
            missingNote(check.missing, check.signerName),
            check.paymentDate?.let(::paymentDateNote),
            check.amountProblem?.let(::amountNote),
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR)
    }

    private const val PAYMENT_DATE_UNREADABLE = "Data da transferência do comprovante Pix/TED não foi lida"

    /** Transferência de outro mês: no PDF com texto, a data é certa; na foto (ou pelo identificador do Pix), não. */
    private fun paymentDateNote(problem: PaymentDateProblem): String {
        val month = Months.name(problem.expected)
        val found = problem.found ?: return "$PAYMENT_DATE_UNREADABLE: confira à mão se é de $month"
        val action = if (problem.uncertain) "confira à mão (foto)" else "confira se a despesa é deste mês"
        return "Comprovante Pix/TED de ${found.format(TransferDates.format)}, mas o mês em edição é $month: $action"
    }

    private val automaticDateNotes = listOf(
        Regex("""${Regex.escape(PAYMENT_DATE_UNREADABLE)}: confira à mão se é de .+"""),
        Regex(
            """Comprovante Pix/TED de \d{2}/\d{2}/\d{4}, mas o mês em edição é .+: """ +
                    """(confira à mão \(foto\)|confira se a despesa é deste mês)""",
        ),
    )

    private fun missingNote(missing: Set<ExpenseRequirement>, signerName: String?): String? {
        val parts = expenseRequirements.filter { it in missing }.map { describe(it, signerName) }
        if (parts.isEmpty()) return null
        val list = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " e " + parts.last()
        return "Falta $list"
    }

    /** Diz os valores lidos e o que fazer: trocar o documento errado ou conferir à mão. */
    private fun amountNote(problem: AmountProblem): String {
        val values = listOfNotNull(problem.paid, problem.fiscal)
        val parts = amountNotes.getValue(problem.kind).split(MONEY_SLOT)
        return parts.first() + parts.drop(1).zip(values)
            .joinToString("") { (text, value) -> Money.format(value) + text }
    }

    /** `%s` = um valor em reais ("1.234,56"), na ordem: comprovante, cupom/nota. */
    private const val MONEY_SLOT = "%s"
    private val amountNotes = mapOf(
        AmountProblemKind.MISMATCH to
                "Valor do comprovante (R$ %s) diferente do cupom/nota (R$ %s): confira e troque o documento errado",
        AmountProblemKind.PAYMENT_UNREADABLE to
                "Valor do comprovante Pix/TED não foi lido: confira à mão se é igual ao total do cupom/nota",
        AmountProblemKind.TOTAL_UNREADABLE to
                "Total do cupom/nota não foi lido: confira à mão se é igual ao comprovante (R$ %s)",
        AmountProblemKind.UNCERTAIN_READING to
                "Valor do comprovante (R$ %s) diferente do lido na foto do cupom/nota (R$ %s), que pode ter sido " +
                "mal lida: confira à mão",
    )

    /**
     * A pendência foi escrita pela conferência automática? Só essas são trocadas ou removidas sozinhas;
     * as que o usuário escreveu nunca são mexidas. Reconhece também os textos de versões anteriores, para que
     * sejam atualizados quando as regras mudam.
     */
    fun isAutomaticNote(note: String): Boolean =
        note == UNREADABLE_NOTE ||
                note.split(NOTE_SEPARATOR).all { part ->
                    automaticMissingNotes.any { it.matches(part) } || automaticAmountNotes.any { it.matches(part) } ||
                            automaticDateNotes.any { it.matches(part) }
                }

    /**
     * Todas as "Falta …" possíveis, com qualquer nome de responsável (trocar o responsável troca a pendência) e
     * os textos antigos ("cupom ou nota fiscal", "assinatura de Elves Lincoln").
     */
    private val automaticMissingNotes: List<Regex> by lazy {
        val all = expenseRequirements
        val subsets =
            (1 until (1 shl all.size)).map { mask -> all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet() }
        subsets.flatMap { listOfNotNull(missingNote(it, NAME_SLOT), missingNote(it, null)) }
            .flatMap { listOf(it, it.replace(FISCAL_DESCRIPTION, LEGACY_FISCAL_DESCRIPTION)) }
            .distinct()
            .map { note -> Regex(note.split(NAME_SLOT).joinToString("""[^;,]+?""") { Regex.escape(it) }) }
    }

    /** Lugar do nome do responsável nos modelos de pendência. */
    private const val NAME_SLOT = "\u0001"

    private val automaticAmountNotes: List<Regex> by lazy {
        (amountNotes.values + LEGACY_NOT_FOUND_NOTE).map { template ->
            Regex(template.split(MONEY_SLOT).joinToString("""[\d.,]+""") { Regex.escape(it) })
        }
    }

    /** Versão 2: Pix sem nenhum valor igual no cupom/nota sem linha de total. */
    private const val LEGACY_NOT_FOUND_NOTE =
        "Valor do comprovante (R$ %s) não aparece no cupom/nota: confira e troque o documento errado"

    private const val FISCAL_DESCRIPTION = "cupom, nota fiscal ou recibo"

    /** Até a versão 2 o recibo não valia. */
    private const val LEGACY_FISCAL_DESCRIPTION = "cupom ou nota fiscal"

    private const val NOTE_SEPARATOR = "; "

    /** Os itens de uma despesa, na ordem da pendência (os outros são dos extratos, em [BankStatementRules]). */
    private val expenseRequirements = listOf(
        ExpenseRequirement.FISCAL_DOCUMENT, ExpenseRequirement.PAYMENT_PROOF, ExpenseRequirement.SIGNATURE,
    )

    private fun describe(requirement: ExpenseRequirement, signerName: String?): String = when (requirement) {
        ExpenseRequirement.FISCAL_DOCUMENT -> FISCAL_DESCRIPTION
        ExpenseRequirement.PAYMENT_PROOF -> "comprovante Pix/TED"
        ExpenseRequirement.SIGNATURE ->
            if (signerName == null) SIGNER_NOT_CONFIGURED else "assinatura de $signerName"
        ExpenseRequirement.PAYMENT_MONTH -> error("A data tem pendência própria (paymentDateNote)")
        ExpenseRequirement.BANK_ACCOUNT, ExpenseRequirement.STATEMENT_MONTH,
        ExpenseRequirement.SHEET_HEADER, ExpenseRequirement.SHEET_MONTH,
        ExpenseRequirement.REPORT_TITLE, ExpenseRequirement.REPORT_MONTH,
        ExpenseRequirement.DONATION_DATA, ExpenseRequirement.DONATION_MONTH, ExpenseRequirement.DONATION_AMOUNT,
        ExpenseRequirement.DONATION_SET, ExpenseRequirement.TRANSFER_HEADER,
        ExpenseRequirement.TRANSFER_TOTAL -> error("Item de outro documento: $requirement")
    }

    /** Ninguém configurado: a pendência diz onde configurar. */
    private const val SIGNER_NOT_CONFIGURED =
        "assinatura do responsável (configure quem assina em Configurações → Assinaturas das despesas)"

    internal const val UNREADABLE_NOTE = "Não foi possível ler o PDF"
}
