package com.bragadev.fiscal.domain.rules

/** Imagens aceitas para virar PDF. */
object ImageFileRules {
    val SUPPORTED_EXTENSIONS = listOf("jpg", "jpeg", "png")

    fun isSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS
}
