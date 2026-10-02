package com.bragadev.fiscal.domain.rules

object FileNameRules {
    const val PDF_EXTENSION = ".pdf"

    private val invalidCharacters = Regex("[<>:\"/\\\\|?*\\x00-\\x1F]")
    private val reservedNames = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    fun hasPdfExtension(fileName: String): Boolean = fileName.endsWith(PDF_EXTENSION, ignoreCase = true)

    fun baseName(fileName: String): String =
        if (hasPdfExtension(fileName)) fileName.dropLast(PDF_EXTENSION.length) else fileName

    fun withPdfExtension(baseName: String): String = "$baseName$PDF_EXTENSION"

    /** Valida um nome de arquivo segundo as regras do Windows. */
    fun isValid(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        if (invalidCharacters.containsMatchIn(fileName)) return false
        if (fileName.endsWith('.') || fileName.endsWith(' ')) return false
        val stem = fileName.substringBefore('.').trim().uppercase()
        return stem !in reservedNames
    }

    /** Compara nomes como o Windows: sem diferenciar maiúsculas de minúsculas. */
    fun containsIgnoringCase(names: Set<String>, candidate: String): Boolean =
        names.any { it.equals(candidate, ignoreCase = true) }
}
