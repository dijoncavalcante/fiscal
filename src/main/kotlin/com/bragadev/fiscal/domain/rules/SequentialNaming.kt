package com.bragadev.fiscal.domain.rules

/**
 * Numeração automática usada pela categoria "Outros": "1. Outros.pdf", "2. Outros.pdf"...
 *
 * O próximo número é sempre o maior número existente + 1. Lacunas (ex.: "2." apagado)
 * não são reaproveitadas, para nunca reutilizar automaticamente um número já usado.
 */
object SequentialNaming {
    fun nextName(label: String, existingNames: Set<String>): String {
        val pattern = patternFor(label)
        val highest = existingNames
            .mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return FileNameRules.withPdfExtension("${highest + 1}. $label")
    }

    fun matches(label: String, fileName: String): Boolean = patternFor(label).matches(fileName)

    private fun patternFor(label: String): Regex =
        Regex("^(\\d+)\\. ${Regex.escape(label)}(?: \\(\\d+\\))?\\.pdf$", RegexOption.IGNORE_CASE)
}
