package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AmountMismatch
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.*

/**
 * Conferência das despesas da Conta da Manutenção ("3. Despesas"): cada PDF precisa ter o cupom ou a nota
 * fiscal, o comprovante do Pix/TED e a assinatura digital (gov.br) de Elves Lincoln Menezes de Lima; e o valor
 * pago no comprovante precisa bater com o cupom/nota.
 *
 * Trabalha só com o texto já extraído (PDF ou OCR) e os nomes dos certificados das assinaturas. O texto é
 * normalizado (sem acentos, maiúsculas, só letras e números) e os termos são procurados palavra por palavra,
 * tolerando poucos erros de letra em palavras longas — o OCR de foto de cupom troca letras com frequência
 * ("Nata Fecal" para "Nota Fiscal").
 */
object ExpenseDocumentRules {
    const val CATEGORY_ID = "manutencao.despesas"
    const val SIGNER_NAME = "Elves Lincoln Menezes de Lima"

    private val signerWords = words(SIGNER_NAME)

    /** Cupom fiscal, nota fiscal (DANFE, NFC-e) ou conta de consumo (água, luz, internet). */
    private val fiscalTerms = listOf(
        "CUPOM FISCAL", "CUPOM", "NFC-e", "NFCe", "NF-e", "NFe", "CF-e", "NOTA FISCAL", "DANFE",
        "DOCUMENTO AUXILIAR", "CHAVE DE ACESSO", "CONSULTA DE AUTENTICIDADE", "COMPROVANTE DE VENDA", "FATURA",
        "KWH", "LEITURA ATUAL", "LEITURA ANTERIOR", "ILUMINACAO PUBLICA",
    ).map(::words)

    private val comprovante = words("COMPROVANTE")
    private val paymentWords = listOf("PIX", "TED", "TRANSFERENCIA").map(::words)
    private val paymentPhrases = listOf("ID DA TRANSACAO", "ID DA TRANSFERENCIA").map(::words)
    private val signatureStamp = words("ASSINADO DIGITALMENTE")

    /** Identificador de uma transação Pix (E2E): "E" + 31 letras/números, ex.: E60746948202609191355M3943eq0hGo. */
    private val pixEndToEndId = Regex("""^E\d{20}[A-Z0-9]{11}$""")

    /** Quantas palavras depois de "assinado digitalmente" o nome pode aparecer no selo. */
    private const val STAMP_WINDOW = 12

    fun check(content: DocumentContent): ExpenseCheck {
        val pages = content.pageTexts.map(::words)
        val missing = buildSet {
            if (pages.none(::hasFiscalDocument)) add(ExpenseRequirement.FISCAL_DOCUMENT)
            if (pages.none(::hasPaymentProof)) add(ExpenseRequirement.PAYMENT_PROOF)
            if (!isSigned(content.signers, pages)) add(ExpenseRequirement.SIGNATURE)
        }
        return ExpenseCheck(missing, amountMismatch = amountMismatch(content, pages))
    }

    /**
     * Valor pago × cupom/nota. O valor pago é o "Valor:" de cada página de comprovante (somado, se houver mais
     * de um). Ele bate se for um valor do cupom/nota ou a soma de valores de cupons/notas diferentes (várias
     * compras pagas num Pix só) — procurar em todos os valores da página, e não só no total, protege contra o
     * OCR que erra o total mas acerta a mesma quantia em outra linha ("Valor bruto").
     *
     * Numa foto (OCR) só aponta diferença quando conseguiu ler a linha do total, e diferença de um único dígito
     * é tratada como erro do OCR. Na dúvida, nada é afirmado (`null`). Conferido nas 95 despesas reais de
     * 2025-2026: nenhuma pendência falsa.
     */
    private fun amountMismatch(content: DocumentContent, pages: List<List<String>>): AmountMismatch? {
        val raw = content.pageTexts.map(::plain)
        val paymentPages = pages.indices.filter { hasPaymentProof(pages[it]) }
        val fiscalPages = pages.indices.filter { hasFiscalDocument(pages[it]) && it !in paymentPages }
        val payments = paymentPages.mapNotNull { paidAmount(raw[it]) }
        if (payments.isEmpty() || fiscalPages.isEmpty()) return null
        val paid = payments.reduce(BigDecimal::add)

        val pageAmounts = fiscalPages.map { amountsIn(raw[it]) + labeledTotals(raw[it]) }
        // Nenhum valor legível no cupom/nota: não dá para comparar.
        if (pageAmounts.all { it.isEmpty() }) return null
        val reachable = reachableSums(pageAmounts) ?: return null
        if (cents(paid) in reachable || payments.all { cents(it) in reachable }) return null

        val totals = fiscalPages.associateWith { mainTotal(raw[it]) }.filterValues { it != null }
        // Sem linha de total: só afirma se o cupom/nota é texto de verdade (DANFE, conta digital) — foto pode ter
        // o valor ilegível.
        if (totals.isEmpty()) {
            return if (fiscalPages.none { it in content.ocrPages }) AmountMismatch(paid, null) else null
        }
        val fiscal = totals.values.filterNotNull().reduce(BigDecimal::add)
        val fromPhoto = totals.keys.any { it in content.ocrPages }
        if (fromPhoto && differsInOneDigit(paid, fiscal)) return null
        return AmountMismatch(paid, fiscal)
    }

    /**
     * Somas possíveis (em centavos) pegando no máximo um valor de cada página de cupom/nota. `null` se as
     * combinações passarem do limite (página cheia de números): aí não dá para afirmar nada.
     */
    private fun reachableSums(pageAmounts: List<List<BigDecimal>>): Set<Long>? {
        var sums = setOf(0L)
        for (amounts in pageAmounts) {
            val values = amounts.map(::cents).toSet()
            sums = sums + sums.flatMap { sum -> values.map { sum + it } }
            if (sums.size > MAX_SUMS) return null
        }
        return sums - 0L
    }

    private const val MAX_SUMS = 50_000

    private fun cents(value: BigDecimal): Long = value.movePointRight(2).toLong()

    /** "781,57" lido como "761,57": mesmo tamanho e só um dígito diferente. */
    private fun differsInOneDigit(a: BigDecimal, b: BigDecimal): Boolean {
        val x = cents(a).toString()
        val y = cents(b).toString()
        return x.length == y.length && x.indices.count { x[it] != y[it] } == 1
    }

    /** Valor de dinheiro: "125,00", "1.234,56" ou "125.00" (como o banco escreve no comprovante). */
    private const val MONEY = """(\d{1,3}(?:\.\d{3})+|\d+)[.,](\d{2})(?!\d)"""
    private val money = Regex("""(?<![\d.,/])$MONEY""")
    private val paidLabel =
        Regex("""(?:VALOR(?: DO PAGAMENTO| PAGO| DA TRANSFERENCIA| TRANSFERIDO| TOTAL)?|PIX ENVIADO)\s*:?\s*(?:R\$)?\s*$MONEY""")
    private val payableLabel = """VALOR A PAGAR|TOTAL A PAGAR|VALOR PAGO"""
    private val totalLabel =
        Regex("""($payableLabel|VALOR TOTAL(?: DA NOTA)?|VALOR LIQUIDO|TOTAL)\D{0,25}?$MONEY""")

    private fun paidAmount(page: String): BigDecimal? =
        paidLabel.findAll(page).map { amount(it.groupValues[1], it.groupValues[2]) }.firstOrNull { it.signum() > 0 }

    private fun amountsIn(page: String): List<BigDecimal> =
        money.findAll(page).map { amount(it.groupValues[1], it.groupValues[2]) }.filter { it.signum() > 0 }.toList()

    private fun labeledTotals(page: String): List<BigDecimal> =
        totalMatches(page).map { it.second }.toList()

    /** Total da página: o "valor a pagar" (já com desconto), senão o último total lido. */
    private fun mainTotal(page: String): BigDecimal? {
        val matches = totalMatches(page).toList()
        return matches.lastOrNull { Regex(payableLabel).matches(it.first) }?.second ?: matches.lastOrNull()?.second
    }

    private fun totalMatches(page: String): Sequence<Pair<String, BigDecimal>> =
        totalLabel.findAll(page).map { it.groupValues[1] to amount(it.groupValues[2], it.groupValues[3]) }
            .filter { it.second.signum() > 0 }

    private fun amount(integer: String, cents: String): BigDecimal = BigDecimal("${integer.replace(".", "")}.$cents")

    /**
     * Texto sem acentos e em maiúsculas, mas com a pontuação (precisa dela para ler valores). Logo depois de
     * "R$" o OCR às vezes perde a vírgula ("R$ 166 92"): ali, e só ali, o espaço vira vírgula.
     */
    private fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "").uppercase()
            .replace(Regex("""\s+"""), " ")
            .replace(lostComma, "$1 $2,$3")

    private val lostComma = Regex("""\b(R\$|RS) ?(\d{1,6}) (\d{2})(?![\d.,])""")

    /** Já tem cupom/nota e comprovante? Usado para parar o OCR assim que o PDF estiver completo. */
    fun hasEnoughText(content: DocumentContent): Boolean {
        val result = check(content)
        return ExpenseRequirement.FISCAL_DOCUMENT !in result.missing && ExpenseRequirement.PAYMENT_PROOF !in result.missing
    }

    private fun hasFiscalDocument(page: List<String>): Boolean = fiscalTerms.any { page.findPhrase(it).any() }

    /**
     * Comprovante Pix/TED: "comprovante" junto de Pix, TED ou transferência na mesma página, ou o
     * identificador da transação. Uma fatura que só diz "pague com Pix" não conta.
     */
    private fun hasPaymentProof(page: List<String>): Boolean =
        (page.findPhrase(comprovante).any() && paymentWords.any { page.findPhrase(it).any() }) ||
                paymentPhrases.any { page.findPhrase(it).any() } || page.any(pixEndToEndId::matches)

    /**
     * Assinado pelo responsável: o certificado de alguma assinatura digital tem o nome dele, ou o texto
     * traz o selo "Documento assinado digitalmente" seguido do nome. Só o nome solto no texto não basta:
     * ele aparece, por exemplo, como recebedor do Pix.
     */
    private fun isSigned(signers: List<String>, pages: List<List<String>>): Boolean =
        signers.any { words(it).findPhrase(signerWords).any() } ||
                pages.any { page ->
                    page.findPhrase(signatureStamp).any { start ->
                        val after = page.subList(
                            start + signatureStamp.size,
                            minOf(page.size, start + signatureStamp.size + STAMP_WINDOW)
                        )
                        after.findPhrase(signerWords).any()
                    }
                }

    /** Pendência gravada para o arquivo, em poucas palavras; `null` se a despesa está completa. */
    fun issueNote(check: ExpenseCheck): String? {
        if (check.unreadable) return UNREADABLE_NOTE
        val parts = listOfNotNull(missingNote(check.missing), check.amountMismatch?.let(::mismatchNote))
        return parts.takeIf { it.isNotEmpty() }?.joinToString(NOTE_SEPARATOR)
    }

    private fun missingNote(missing: Set<ExpenseRequirement>): String? {
        if (missing.isEmpty()) return null
        val parts = ExpenseRequirement.entries.filter { it in missing }.map(::describe)
        val list = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " e " + parts.last()
        return "Falta $list"
    }

    /** Diz os dois valores e o que fazer: o usuário confere qual documento está errado e troca. */
    private fun mismatchNote(mismatch: AmountMismatch): String {
        val paid = "Valor do comprovante (R$ ${formatMoney(mismatch.paid)})"
        val fiscal =
            mismatch.fiscal?.let { "diferente do cupom/nota (R$ ${formatMoney(it)})" } ?: "não aparece no cupom/nota"
        return "$paid $fiscal: confira e troque o documento errado"
    }

    /**
     * A pendência foi escrita pela conferência automática? Só essas são trocadas ou removidas sozinhas;
     * as que o usuário escreveu nunca são mexidas.
     */
    fun isAutomaticNote(note: String): Boolean =
        note == UNREADABLE_NOTE ||
                note.split(NOTE_SEPARATOR).all { it in missingNotes || automaticMismatchNote.matches(it) }

    private val missingNotes: Set<String> by lazy {
        val all = ExpenseRequirement.entries
        val subsets =
            (1 until (1 shl all.size)).map { mask -> all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet() }
        subsets.mapNotNull(::missingNote).toSet()
    }

    private val automaticMismatchNote = Regex(
        """Valor do comprovante \(R\$ [\d.,]+\) (diferente do cupom/nota \(R\$ [\d.,]+\)|não aparece no cupom/nota): """ +
                """confira e troque o documento errado""",
    )

    private const val NOTE_SEPARATOR = "; "

    /** "1.234,56", como o usuário escreve. */
    private fun formatMoney(value: BigDecimal): String =
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))).format(value)

    private fun describe(requirement: ExpenseRequirement): String = when (requirement) {
        ExpenseRequirement.FISCAL_DOCUMENT -> "cupom ou nota fiscal"
        ExpenseRequirement.PAYMENT_PROOF -> "comprovante Pix/TED"
        ExpenseRequirement.SIGNATURE -> "assinatura de Elves Lincoln"
    }

    private const val UNREADABLE_NOTE = "Não foi possível ler o PDF"

    /** Sem acentos, em maiúsculas, separado em palavras de letras e números ("NF-e" vira "NF", "E"). */
    private fun words(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .uppercase()
            .split(Regex("""[^A-Z0-9]+"""))
            .filter { it.isNotEmpty() }

    /** Posições onde as palavras de [phrase] aparecem em sequência, tolerando erros de OCR. */
    private fun List<String>.findPhrase(phrase: List<String>): Sequence<Int> {
        val isPhrase = phrase.size > 1
        return (0..size - phrase.size).asSequence().filter { start ->
            phrase.indices.all { i -> similar(this[start + i], phrase[i], isPhrase) }
        }
    }

    /**
     * Palavra lida igual à esperada, com poucos erros: nenhum até 3 letras, 1 até 5 letras, 2 a partir de 8
     * (ou de 6, dentro de uma expressão de várias palavras — o resto da expressão confirma).
     */
    private fun similar(found: String, expected: String, inPhrase: Boolean): Boolean {
        val allowed = when {
            expected.length <= 3 -> 0
            expected.length <= 5 -> 1
            expected.length >= 8 || inPhrase -> 2
            else -> 1
        }
        if (allowed == 0) return found == expected
        return kotlin.math.abs(found.length - expected.length) <= allowed && distance(
            found,
            expected,
            allowed
        ) <= allowed
    }

    /** Distância de edição (Levenshtein), parando cedo quando passa de [limit]. */
    private fun distance(a: String, b: String, limit: Int): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > limit) return rowMin
            previous = current
        }
        return previous[b.length]
    }
}
