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

    /** Extensão com o ponto (".pdf", ".jpg"); vazio se o nome não tiver. */
    fun extension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(dot) else ""
    }

    /**
     * Novo nome digitado pelo usuário mantendo a extensão do arquivo atual: "Recibo" em "foto.jpg" → "Recibo.jpg".
     * Se o usuário já digitou a mesma extensão, ela não se repete. Vazio = o usuário não escreveu um nome.
     */
    fun keepingExtension(currentName: String, typedName: String): String {
        val extension = extension(currentName)
        val typed = typedName.trim()
        val base = if (extension.isNotEmpty() && typed.endsWith(extension, ignoreCase = true)) {
            typed.dropLast(extension.length).trim()
        } else {
            typed
        }
        return if (base.isEmpty()) "" else "$base$extension"
    }

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
