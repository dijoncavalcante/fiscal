package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.NamingRule

/**
 * Nome padrão do arquivo dentro da pasta do mês.
 *
 * - "8. Extrato Bancário" → "8. Extrato Bancário.pdf"; "5.1 Comprovante Remessa" → "5.1 Comprovante Remessa.pdf"
 * - "Outros" → "1. Outros.pdf", "2. Outros.pdf"...
 * - "3. Despesas" → "3. Despesa - xxx.pdf", "3.1 Despesa - yyy.pdf"... (descrição informada pelo usuário)
 */
object CategoryNaming {
    /**
     * @param description obrigatória para [NamingRule.DESCRIBED_SEQUENCE]; ignorada nas demais regras.
     * @param currentFileName nome atual quando o arquivo já está na pasta de destino: uma despesa
     *   já numerada mantém o número e só troca a descrição.
     * @param sequenceIndex posição escolhida pelo usuário (0 = "3.", 2 = "3.2"); tem prioridade sobre as demais.
     */
    fun suggestedName(
        category: DocumentCategory,
        existingNamesInTarget: Set<String>,
        description: String? = null,
        currentFileName: String? = null,
        sequenceIndex: Int? = null,
    ): String? = when (category.namingRule) {
        NamingRule.CATEGORY_NAME -> FileNameRules.withPdfExtension(category.label)
        NamingRule.SEQUENTIAL -> SequentialNaming.nextName(category.name, existingNamesInTarget)
        NamingRule.DESCRIBED_SEQUENCE -> description?.takeIf { it.isNotBlank() }?.let {
            val index = sequenceIndex
                ?: currentFileName?.let { name -> DescribedSequenceNaming.indexOf(category, name) }
                ?: DescribedSequenceNaming.nextIndex(category, existingNamesInTarget)
            DescribedSequenceNaming.fileName(category, index, it)
        }
    }

    /** Arquivo que já segue o padrão da categoria; para descritas, quem decide é o nome final. */
    fun isAlreadyNamedFor(category: DocumentCategory, fileName: String): Boolean = when (category.namingRule) {
        NamingRule.CATEGORY_NAME -> fileName.equals(FileNameRules.withPdfExtension(category.label), ignoreCase = true)
        NamingRule.SEQUENTIAL -> SequentialNaming.matches(category.name, fileName)
        NamingRule.DESCRIBED_SEQUENCE -> false
    }
}
