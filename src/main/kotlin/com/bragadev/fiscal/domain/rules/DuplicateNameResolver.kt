package com.bragadev.fiscal.domain.rules

/**
 * Gera o próximo nome livre para uma cópia numerada.
 *
 * "Extrato Bancário.pdf" já existe → "Extrato Bancário (2).pdf",
 * depois "Extrato Bancário (3).pdf" e assim por diante.
 */
object DuplicateNameResolver {
    private const val FIRST_COPY_NUMBER = 2

    fun nextNumberedCopy(fileName: String, existingNames: Set<String>): String {
        val base = FileNameRules.baseName(fileName)
        return generateSequence(FIRST_COPY_NUMBER) { it + 1 }
            .map { FileNameRules.withPdfExtension("$base ($it)") }
            .first { !FileNameRules.containsIgnoringCase(existingNames, it) }
    }
}
