package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.NamingRule

/** Nome padrão do arquivo de acordo com a regra de nomenclatura da categoria. */
object CategoryNaming {
    fun suggestedName(category: DocumentCategory, existingNamesInTarget: Set<String>): String =
        when (category.namingRule) {
            NamingRule.CATEGORY_NAME -> FileNameRules.withPdfExtension(category.name)
            NamingRule.SEQUENTIAL -> SequentialNaming.nextName(category.name, existingNamesInTarget)
        }
}
