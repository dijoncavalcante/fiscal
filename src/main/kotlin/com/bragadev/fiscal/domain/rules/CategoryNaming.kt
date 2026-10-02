package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.NamingRule

/**
 * Nome padrão do arquivo dentro da pasta do mês.
 *
 * "8. Extrato Bancário" → "8. Extrato Bancário.pdf"; "5.1 Comprovante Remessa" → "5.1 Comprovante Remessa.pdf";
 * "Outros" → "1. Outros.pdf", "2. Outros.pdf"...
 */
object CategoryNaming {
    fun suggestedName(category: DocumentCategory, existingNamesInTarget: Set<String>): String =
        when (category.namingRule) {
            NamingRule.CATEGORY_NAME -> FileNameRules.withPdfExtension(category.label)
            NamingRule.SEQUENTIAL -> SequentialNaming.nextName(category.name, existingNamesInTarget)
        }

    fun isAlreadyNamedFor(category: DocumentCategory, fileName: String): Boolean = when (category.namingRule) {
        NamingRule.CATEGORY_NAME -> fileName.equals(FileNameRules.withPdfExtension(category.label), ignoreCase = true)
        NamingRule.SEQUENTIAL -> SequentialNaming.matches(category.name, fileName)
    }
}
