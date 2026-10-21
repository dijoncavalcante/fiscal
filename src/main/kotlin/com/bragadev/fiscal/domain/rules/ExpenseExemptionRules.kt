package com.bragadev.fiscal.domain.rules

/**
 * Despesas da Conta da Congregação que dispensam o cupom (Caso A): o **nome do arquivo** tem um dos termos
 * configurados ("Resolução mensal", "Pagamento ônibus Assembleia", "Donativos VTM"…). Nos PDFs reais esses termos
 * não aparecem no texto, só no nome.
 *
 * Um termo vale quando todas as suas palavras principais estão no nome, em qualquer ordem, sem acento, sem
 * diferenciar maiúsculas e aceitando plural ("Donativo" vale por "DONATIVOS"; "Depósito de publicações" por
 * "DEPÓSITO DE PUBLICAÇÃO"). Palavras curtas de ligação ("para", "a", "de") são ignoradas: "Ônibus para a
 * assembleia" vale por "Pagamento ônibus Assembleia". O nome é digitado pelo usuário, não lido por OCR: a
 * comparação é exata, sem tolerar letras trocadas (assim "Impressão convite do congresso" não vira ônibus).
 */
object ExpenseExemptionRules {
    private val connectives =
        setOf("A", "AS", "O", "OS", "DE", "DA", "DAS", "DO", "DOS", "PARA", "E", "EM", "NA", "NO", "NAS", "NOS")

    /** O termo que dispensa o cupom para este arquivo; `null` se nenhum (cupom obrigatório). */
    fun exemptBy(fileName: String, terms: List<String>): String? {
        val nameWords = significantWords(fileName.substringBeforeLast('.')).toSet()
        return terms.firstOrNull { term ->
            val termWords = significantWords(term)
            termWords.isNotEmpty() && termWords.all { it in nameWords }
        }
    }

    /** Palavras sem acento, em maiúsculas e no singular, sem as de ligação. */
    private fun significantWords(text: String): List<String> =
        FuzzyText.words(text).filter { it !in connectives && it.any(Char::isLetter) }.map(::singular)

    /** "PUBLICACOES" → "PUBLICACAO", "DONATIVOS" → "DONATIVO", "OBJETIVOS" → "OBJETIVO". */
    private fun singular(word: String): String = when {
        word.length > 4 && (word.endsWith("OES") || word.endsWith("AES")) -> word.dropLast(3) + "AO"
        word.length > 3 && word.endsWith("S") -> word.dropLast(1)
        else -> word
    }
}
