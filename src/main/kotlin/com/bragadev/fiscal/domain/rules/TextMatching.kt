package com.bragadev.fiscal.domain.rules

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.*

/** Comparação de palavras tolerante aos erros do OCR, usada pelas regras de conferência das despesas. */
internal object FuzzyText {
    /** Sem acentos, em maiúsculas, separado em palavras de letras e números ("NF-e" vira "NF", "E"). */
    fun words(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .uppercase()
            .split(Regex("""[^A-Z0-9]+"""))
            .filter { it.isNotEmpty() }

    /** Posições onde as palavras de [phrase] aparecem em sequência, tolerando erros de OCR. */
    fun List<String>.findPhrase(phrase: List<String>): Sequence<Int> {
        val isPhrase = phrase.size > 1
        return (0..size - phrase.size).asSequence().filter { start ->
            phrase.indices.all { i -> similar(this[start + i], phrase[i], isPhrase) }
        }
    }

    /**
     * Palavra lida igual à esperada, com poucos erros: nenhum até 3 letras, 1 até 5 letras, 2 a partir de 8
     * (ou de 6, dentro de uma expressão de várias palavras — o resto da expressão confirma).
     */
    fun similar(found: String, expected: String, inPhrase: Boolean): Boolean {
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
    fun distance(a: String, b: String, limit: Int): Int {
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

/** Valores em dinheiro escritos nos cupons, notas e comprovantes. */
internal object Money {
    /**
     * Valor de dinheiro: "125,00", "1.234,56", "125.00" ou "1,051.49" (como alguns bancos escrevem no
     * comprovante). Grupos: parte inteira, centavos.
     */
    const val PATTERN = """(\d{1,3}(?:,\d{3})+(?=\.\d{2})|\d{1,3}(?:\.\d{3})+|\d+)[.,](\d{2})(?!\d)"""

    private val wholeWord = Regex("^$PATTERN$")

    fun amount(integer: String, cents: String): BigDecimal =
        BigDecimal("${integer.replace(".", "").replace(",", "")}.$cents")

    /**
     * Valor de uma palavra sozinha lida pelo OCR, com a pontuação em volta ("50,00)", "R$50,00", "50,00.");
     * `null` se não é um valor (ex.: a chave de acesso, "1", "0,00").
     */
    fun inWord(word: String): BigDecimal? {
        val withoutCurrency = word.uppercase().substringAfter("R$").removePrefix("RS")
        val digits = withoutCurrency.trim('(', ')', '|', ':', ';', '.', ',', ' ', '\'', '"', '‘', '’')
        val match = wholeWord.find(digits) ?: return null
        return amount(match.groupValues[1], match.groupValues[2]).takeIf { it.signum() > 0 }
    }

    fun cents(value: BigDecimal): Long = value.movePointRight(2).toLong()

    /** "1.234,56", como o usuário escreve. */
    fun format(value: BigDecimal): String =
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))).format(value)
}
