package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentCategory

/**
 * Categorias com vários arquivos descritos pelo usuário, como Despesas:
 *
 * "3. Despesa - xxx.pdf" → "3.1 Despesa - yyy.pdf" → "3.2 Despesa - xyz.pdf"
 *
 * - O próximo número é sempre o maior existente + 1 (nunca reaproveita lacunas).
 * - Contam todos os arquivos que começam com o número da categoria, mesmo com nomes fora
 *   do padrão, como "3 Despesa - Resolução.pdf".
 * - Um arquivo que já é desta categoria mantém o seu número quando só a descrição muda.
 */
object DescribedSequenceNaming {

    fun fileName(category: DocumentCategory, index: Int, description: String): String =
        FileNameRules.withPdfExtension("${prefix(category.number, index)} ${category.fileWord} - ${description.trim()}")

    /**
     * Número digitado pelo usuário → posição na sequência: "3" ou "3." → 0, "3.1" → 1, "3.2" → 2.
     * Retorna `null` se não for um número desta categoria (ex.: "4.1" em Despesas).
     */
    fun parseIndex(category: DocumentCategory, text: String): Int? {
        val match = Regex("""^\s*${Regex.escape(category.number)}(?:\.(\d*))?\s*$""").matchEntire(text) ?: return null
        val sub = match.groupValues[1]
        return if (sub.isEmpty()) 0 else sub.toIntOrNull()?.takeIf { it > 0 }
    }

    /** Texto do número para mostrar ao usuário: 0 → "3", 2 → "3.2". */
    fun numberText(category: DocumentCategory, index: Int): String =
        if (index == 0) category.number else "${category.number}.$index"

    fun nextIndex(category: DocumentCategory, existingNames: Collection<String>): Int {
        val used = existingNames.mapNotNull { indexOf(category, it) }
        return if (used.isEmpty()) 0 else used.max() + 1
    }

    /** 0 para "3.", 1 para "3.1", 2 para "3.2"; `null` se o arquivo não for desta categoria. */
    fun indexOf(category: DocumentCategory, fileName: String): Int? {
        val match = numberPattern(category).find(fileName) ?: return null
        return match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
    }

    /** Descrição de um arquivo já no padrão, ex.: "3.1 Despesa - ônibus.pdf" → "ônibus". */
    fun descriptionOf(category: DocumentCategory, fileName: String): String? =
        describedPattern(category).matchEntire(fileName)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Sugestão de descrição a partir do nome original do arquivo, sem repetir a palavra da categoria:
     * "Despesa_-_Compra_de_cartazes.pdf" → "Compra de cartazes".
     */
    fun suggestDescription(category: DocumentCategory, fileName: String): String {
        descriptionOf(category, fileName)?.let { return it }
        val words = listOf(category.fileWord, category.name).joinToString("|") { Regex.escape(it) }
        return FileNameRules.baseName(fileName)
            .replace('_', ' ')
            .replace(Regex("""^\s*(?:$words)\b\s*-?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun prefix(number: String, index: Int): String = if (index == 0) "$number." else "$number.$index"

    private fun numberPattern(category: DocumentCategory) =
        Regex("""^${Regex.escape(category.number)}(?:\.(\d+))?(?=[.\s\-_])""")

    private fun describedPattern(category: DocumentCategory) = Regex(
        """^${Regex.escape(category.number)}(?:\.\d+)?\.?\s*${Regex.escape(category.fileWord)}\s*-\s*(.*)\.pdf$""",
        RegexOption.IGNORE_CASE,
    )
}
