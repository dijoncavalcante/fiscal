package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentCategory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamingRulesTest {
    private val extratoBancario = DocumentCategory("extrato_bancario", "Extrato Bancário", "8", AccountType.CONGREGACAO)
    private val outros = DefaultCategories.all.first { it.id == DefaultCategories.OUTROS_ID }

    @Test
    fun `nome da categoria gera nome do arquivo sem o numero`() {
        assertEquals("Extrato Bancário.pdf", CategoryNaming.suggestedName(extratoBancario, emptySet()))
    }

    @Test
    fun `subcategoria gera nome sem o numero`() {
        val comprovante = DefaultCategories.all.first { it.number == "5.1" }
        assertEquals("Comprovante Remessa.pdf", CategoryNaming.suggestedName(comprovante, emptySet()))
    }

    @Test
    fun `nome da pasta separa numero e nome`() {
        assertEquals("8. Extrato Bancário", extratoBancario.folderName)
        assertEquals("5.1 Comprovante Remessa", DefaultCategories.all.first { it.number == "5.1" }.folderName)
        assertEquals("Outros", outros.folderName)
    }

    @Test
    fun `arquivo duplicado gera copia numerada a partir de 2`() {
        val existing = setOf("Extrato Bancário.pdf")
        assertEquals("Extrato Bancário (2).pdf", DuplicateNameResolver.nextNumberedCopy("Extrato Bancário.pdf", existing))
    }

    @Test
    fun `copia numerada pula numeros ocupados`() {
        val existing = setOf("Extrato Bancário.pdf", "Extrato Bancário (2).pdf", "extrato bancário (3).PDF")
        assertEquals("Extrato Bancário (4).pdf", DuplicateNameResolver.nextNumberedCopy("Extrato Bancário.pdf", existing))
    }

    @Test
    fun `outros sem arquivos existentes comeca em 1`() {
        assertEquals("1. Outros.pdf", CategoryNaming.suggestedName(outros, emptySet()))
    }

    @Test
    fun `outros continua apos o maior numero existente`() {
        val existing = setOf("1. Outros.pdf", "2. Outros.pdf")
        assertEquals("3. Outros.pdf", CategoryNaming.suggestedName(outros, existing))
    }

    @Test
    fun `outros nunca reaproveita lacunas`() {
        val existing = setOf("1. Outros.pdf", "3. Outros.pdf", "nota.pdf")
        assertEquals("4. Outros.pdf", SequentialNaming.nextName("Outros", existing))
    }

    @Test
    fun `nomes invalidos no windows sao rejeitados`() {
        assertTrue(FileNameRules.isValid("Extrato Bancário.pdf"))
        assertFalse(FileNameRules.isValid("a:b.pdf"))
        assertFalse(FileNameRules.isValid("CON.pdf"))
        assertFalse(FileNameRules.isValid("  "))
        assertFalse(FileNameRules.isValid("arquivo."))
    }
}
