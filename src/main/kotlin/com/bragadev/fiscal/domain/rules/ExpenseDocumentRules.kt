package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import java.text.Normalizer

/**
 * Conferência das despesas da Conta da Manutenção ("3. Despesas"): cada PDF precisa ter o cupom ou a nota
 * fiscal, o comprovante do Pix/TED e a assinatura digital (gov.br) de Elves Lincoln Menezes de Lima.
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
        return ExpenseCheck(missing)
    }

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
        if (check.missing.isEmpty()) return null
        val parts = ExpenseRequirement.entries.filter { it in check.missing }.map(::describe)
        val list = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " e " + parts.last()
        return "Falta $list"
    }

    /**
     * A pendência foi escrita pela conferência automática? Só essas são trocadas ou removidas sozinhas;
     * as que o usuário escreveu nunca são mexidas.
     */
    fun isAutomaticNote(note: String): Boolean = note in automaticNotes

    private val automaticNotes: Set<String> by lazy {
        val all = ExpenseRequirement.entries
        val subsets =
            (1 until (1 shl all.size)).map { mask -> all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet() }
        subsets.mapNotNull { issueNote(ExpenseCheck(it)) }.toSet() + UNREADABLE_NOTE
    }

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
