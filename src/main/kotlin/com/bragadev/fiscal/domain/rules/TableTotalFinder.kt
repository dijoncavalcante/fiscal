package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.PageLayout
import com.bragadev.fiscal.domain.model.PageWord
import com.bragadev.fiscal.domain.rules.FuzzyText.findPhrase
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Um total do cupom/nota, achado pelo título ("TOTAL", "VALOR A PAGAR", "VALOR TOTAL DA NOTA").
 *
 * @param label título como foi lido (ex.: "TOTAL (R$)").
 * @param confidence certeza do OCR: o menor entre título e valor (100 para texto do próprio PDF).
 * @param isPayable título de "valor a pagar" (já com desconto): é o total que vale quando há vários.
 * @param confirmedBySum a conta da mesma tabela fecha: SubTotal − Desconto + Frete + Acréscimo = Total.
 */
data class TotalCandidate(
    val value: BigDecimal,
    val page: Int,
    val label: String,
    val place: TotalPlace,
    val fromOcr: Boolean,
    val confidence: Float = 100f,
    val isPayable: Boolean = false,
    val confirmedBySum: Boolean = false,
)

/** Onde o valor estava em relação ao título. */
enum class TotalPlace {
    /** Logo depois do título, no texto corrido. */
    TEXT,

    /** À direita do título, na mesma linha. */
    RIGHT,

    /** Embaixo do título, na mesma coluna (tabela: "TOTAL (R$)" em cima, "50,00" embaixo). */
    BELOW,
}

/**
 * Acha o total de um cupom/nota pela posição das palavras na página. Cupons (NFC-e) e notas (DANFE)
 * imprimem o total numa tabela: o título fica em cima e o valor embaixo, às vezes com outros números no meio
 * ("Itens 1 … 50,00"), o que o texto corrido não consegue ligar.
 *
 * Só títulos de total valem: "SubTotal", "Total dos produtos", "Qtde total de itens", "Tributos totais" e a
 * coluna "TOTAL" da lista de itens (linha com "Unitário", "Código"…) não são o total da compra.
 */
object TableTotalFinder {
    private class Label(text: String, val isPayable: Boolean) {
        val tokens = FuzzyText.words(text)
    }

    // Os mais longos primeiro: "VALOR TOTAL DA NOTA" antes de "TOTAL".
    private val labels = listOf(
        Label("VALOR A PAGAR", true),
        Label("TOTAL A PAGAR", true),
        // OCR que perde o "a": "VALOR & PAGAR".
        Label("VALOR PAGAR", true),
        Label("VALOR TOTAL DA NOTA", false),
        Label("VALOR TOTAL", false),
        Label("TOTAL", false),
    )

    /** Depois do título: é o total de outra coisa ("Total dos produtos", "Qtde total de itens"). */
    private val notTotalAfter = setOf("DOS", "DAS", "DE", "DO", "ITENS", "TRIBUTOS", "IMPOSTOS", "PRODUTOS")

    /** Antes do título: "Sub-Total", "Qtde total". */
    private val notTotalBefore = setOf("SUB", "QTDE", "QTD")

    /** Cabeçalho da lista de itens: a coluna "TOTAL" ali é o total de cada item. */
    private val itemHeader = listOf("UNITARIO", "UNIT", "QTD", "QTDE", "CODIGO", "DESCRICAO").map(FuzzyText::words)

    /** Quantas linhas abaixo do título o valor pode estar. */
    private const val LINES_BELOW = 3.5f

    /** Palavras sem número que podem ficar entre o título e o valor ("(R$)", "R$", ":"). */
    private const val MAX_SKIPPED_WORDS = 3

    fun find(layout: PageLayout): List<TotalCandidate> {
        val words = layout.words.filter { it.text.isNotBlank() }
        val lines = lines(words)
        val headers = lines.map(::isItemHeader)
        return lines.flatMapIndexed { i, line ->
            // Cabeçalho da lista de itens quebrado em duas linhas ("… Valor" em cima, "total" embaixo).
            val belowHeader = i > 0 && headers[i - 1] && line.minOf { it.y } - lines[i - 1].maxOf { it.bottom } <
                    1.5f * line.maxOf { it.height }
            candidatesIn(line, words, layout, inItemHeader = headers[i] || belowHeader)
        }
    }

    private fun isItemHeader(line: List<PageWord>): Boolean {
        val texts = line.flatMap { FuzzyText.words(it.text) }
        return itemHeader.any { texts.findPhrase(it).any() }
    }

    /** Texto de cada linha, na ordem da página (para procurar o "Valor:" do comprovante numa nova leitura). */
    fun lineTexts(layout: PageLayout): List<String> =
        lines(layout.words.filter { it.text.isNotBlank() }).map { line -> line.joinToString(" ") { it.text } }

    /** Agrupa as palavras em linhas: mesma altura na página, da esquerda para a direita. */
    fun lines(words: List<PageWord>): List<List<PageWord>> {
        val lines = mutableListOf<MutableList<PageWord>>()
        for (word in words.sortedBy { it.centerY }) {
            val line = lines.lastOrNull()
            val lineCenter = line?.map { it.centerY }?.average()?.toFloat()
            val lineHeight = line?.maxOf { it.height } ?: 0f
            if (line != null && abs(word.centerY - lineCenter!!) <= 0.5f * max(word.height, lineHeight)) {
                line += word
            } else {
                lines += mutableListOf(word)
            }
        }
        return lines.map { line -> line.sortedBy { it.x } }
    }

    /** Palavra da linha de onde cada parte normalizada veio ("Sub-Total" vira "SUB" e "TOTAL", da mesma palavra). */
    private class Token(val text: String, val word: Int)

    private fun candidatesIn(
        line: List<PageWord>, allWords: List<PageWord>, layout: PageLayout, inItemHeader: Boolean,
    ): List<TotalCandidate> {
        val tokens = line.flatMapIndexed { index, word -> FuzzyText.words(word.text).map { Token(it, index) } }
        val texts = tokens.map { it.text }
        val found = mutableListOf<TotalCandidate>()
        var t = 0
        while (t < tokens.size) {
            val label = labels.firstOrNull { label ->
                t + label.tokens.size <= tokens.size &&
                        label.tokens.indices.all { i ->
                            FuzzyText.similar(texts[t + i], label.tokens[i], label.tokens.size > 1)
                        }
            }
            if (label == null) {
                t++
                continue
            }
            val end = t + label.tokens.size
            val excluded = texts.getOrNull(end) in notTotalAfter || texts.getOrNull(t - 1) in notTotalBefore ||
                    (inItemHeader && !label.isPayable)
            if (!excluded) {
                candidateFor(line, tokens[t].word, tokens[end - 1].word, label, allWords, layout)?.let(found::add)
            }
            t = end
        }
        return found
    }

    private fun candidateFor(
        line: List<PageWord>, first: Int, last: Int, label: Label, allWords: List<PageWord>, layout: PageLayout,
    ): TotalCandidate? {
        var labelWords = line.subList(first, last + 1)
        // O "(R$)" logo depois do título faz parte dele: alarga a coluna.
        line.getOrNull(last + 1)?.takeIf(::isCurrencyMark)?.let { labelWords = labelWords + it }
        val labelText = labelWords.joinToString(" ") { it.text }
        val labelConfidence = labelWords.minOf { it.confidence }

        valueToTheRight(line, last)?.let { (word, value) ->
            return TotalCandidate(
                value, layout.page, labelText, TotalPlace.RIGHT, layout.fromOcr,
                min(labelConfidence, word.confidence), label.isPayable,
            )
        }
        val box = Box.of(labelWords)
        val below = valueBelow(box, allWords) ?: return null
        return TotalCandidate(
            below.second, layout.page, labelText, TotalPlace.BELOW, layout.fromOcr,
            min(labelConfidence, below.first.confidence), label.isPayable,
            confirmedBySum = sumMatches(line, box, below, allWords),
        )
    }

    private fun valueToTheRight(line: List<PageWord>, last: Int): Pair<PageWord, BigDecimal>? {
        var skipped = 0
        for (word in line.drop(last + 1)) {
            Money.inWord(word.text)?.let { return word to it }
            // Outro número no caminho ("Itens 1") ou palavras demais: o valor não é deste título.
            if (word.text.any(Char::isDigit) || ++skipped > MAX_SKIPPED_WORDS) return null
        }
        return null
    }

    /** Valor mais perto embaixo do título, na mesma coluna (com folga de uma letra para cada lado). */
    private fun valueBelow(box: Box, allWords: List<PageWord>): Pair<PageWord, BigDecimal>? {
        val margin = box.height
        return allWords.asSequence()
            .filter { it.y >= box.bottom - 0.3f * box.height && it.y <= box.bottom + LINES_BELOW * box.height }
            .filter { overlap(it, box.x - margin, box.right + margin) > 0f }
            .mapNotNull { word -> Money.inWord(word.text)?.let { word to it } }
            .sortedWith(compareBy<Pair<PageWord, BigDecimal>> { it.first.y }
                .thenByDescending { overlap(it.first, box.x, box.right) })
            .firstOrNull()
    }

    /**
     * Na tabela de totais da NFC-e ("SubTotal (R$) Desconto (R$) Frete (R$) TOTAL (R$)") a conta precisa fechar
     * com os valores da mesma linha de baixo. Se fecha, o total foi lido certo, mesmo com confiança baixa.
     */
    private fun sumMatches(
        header: List<PageWord>, totalBox: Box, total: Pair<PageWord, BigDecimal>, allWords: List<PageWord>,
    ): Boolean {
        fun valueUnder(name: String): BigDecimal? {
            val expected = FuzzyText.words(name).single()
            val word = header.firstOrNull { w ->
                val parts = FuzzyText.words(w.text)
                parts.any { FuzzyText.similar(it, expected, inPhrase = false) } ||
                        (expected == "SUBTOTAL" && parts.firstOrNull() == "SUB")
            } ?: return null
            val (valueWord, value) = valueBelow(Box.of(listOf(word)), allWords) ?: return null
            // Precisa estar na mesma linha do total.
            return value.takeIf { abs(valueWord.centerY - total.first.centerY) < totalBox.height }
        }

        val subtotal = valueUnder("SUBTOTAL") ?: return false
        val discount = valueUnder("DESCONTO") ?: BigDecimal.ZERO
        val shipping = valueUnder("FRETE") ?: BigDecimal.ZERO
        val surcharge = valueUnder("ACRESCIMO") ?: BigDecimal.ZERO
        return (subtotal - discount + shipping + surcharge).compareTo(total.second) == 0
    }

    private fun isCurrencyMark(word: PageWord): Boolean =
        word.text.length <= 5 && word.text.none(Char::isDigit) && word.text.contains('R', ignoreCase = true)

    private fun overlap(word: PageWord, from: Float, to: Float): Float = min(word.right, to) - max(word.x, from)

    private class Box(val x: Float, val right: Float, val bottom: Float, val height: Float) {
        companion object {
            fun of(words: List<PageWord>) = Box(
                x = words.minOf { it.x },
                right = words.maxOf { it.right },
                bottom = words.maxOf { it.bottom },
                height = words.maxOf { it.height },
            )
        }
    }
}
