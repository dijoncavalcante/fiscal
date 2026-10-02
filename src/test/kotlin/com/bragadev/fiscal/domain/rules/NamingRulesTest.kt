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
    fun `nome do arquivo segue o padrao da pasta do mes com numero`() {
        assertEquals("8. Extrato Bancário.pdf", CategoryNaming.suggestedName(extratoBancario, emptySet()))
    }

    @Test
    fun `subcategoria usa numero composto sem ponto extra`() {
        val comprovante = DefaultCategories.all.first { it.number == "5.1" }
        assertEquals("5.1 Comprovante Remessa.pdf", CategoryNaming.suggestedName(comprovante, emptySet()))
    }

    @Test
    fun `rotulo separa numero e nome`() {
        assertEquals("8. Extrato Bancário", extratoBancario.label)
        assertEquals("Outros", outros.label)
    }

    @Test
    fun `arquivo duplicado gera copia numerada a partir de 2`() {
        val existing = setOf("8. Extrato Bancário.pdf")
        assertEquals("8. Extrato Bancário (2).pdf", DuplicateNameResolver.nextNumberedCopy("8. Extrato Bancário.pdf", existing))
    }

    @Test
    fun `copia numerada pula numeros ocupados`() {
        val existing = setOf("8. Extrato Bancário.pdf", "8. Extrato Bancário (2).pdf", "8. extrato bancário (3).PDF")
        assertEquals("8. Extrato Bancário (4).pdf", DuplicateNameResolver.nextNumberedCopy("8. Extrato Bancário.pdf", existing))
    }

    @Test
    fun `outros sem arquivos existentes comeca em 1`() {
        assertEquals("1. Outros.pdf", CategoryNaming.suggestedName(outros, emptySet()))
    }

    @Test
    fun `outros continua apos o maior numero existente`() {
        val existing = setOf("1. Outros.pdf", "2. Outros.pdf", "1. Folha de Contas.pdf")
        assertEquals("3. Outros.pdf", CategoryNaming.suggestedName(outros, existing))
    }

    @Test
    fun `outros nunca reaproveita lacunas`() {
        val existing = setOf("1. Outros.pdf", "3. Outros.pdf", "nota.pdf")
        assertEquals("4. Outros.pdf", SequentialNaming.nextName("Outros", existing))
    }

    @Test
    fun `nomes invalidos no windows sao rejeitados`() {
        assertTrue(FileNameRules.isValid("8. Extrato Bancário.pdf"))
        assertFalse(FileNameRules.isValid("a:b.pdf"))
        assertFalse(FileNameRules.isValid("CON.pdf"))
        assertFalse(FileNameRules.isValid("  "))
        assertFalse(FileNameRules.isValid("arquivo."))
    }
}
