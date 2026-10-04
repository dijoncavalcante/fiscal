package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Usa nomes reais encontrados nas pastas de junho e agosto de 2026. */
class MonthChecklistBuilderTest {
    private val hierarchy = CategoryHierarchy(DefaultCategories.all)
    private val congregacao = DefaultCategories.all.filter { hierarchy.isAllowedIn(it, AccountType.CONGREGACAO) }

    @Test
    fun `associa arquivos pelo numero mesmo com nomes diferentes`() {
        val june = listOf(
            "1. Folha de Contas (S-26).pdf",
            "2. Recibos - Junho.pdf",
            "3. Despesa -RESOLUÇÃO MENSAL COMISSÃO DE FUNCIONAMENTO.pdf",
            "3.1 Despesa - ônibus para o congresso.pdf",
            "4. Registro de Transferência de Fundos (TO-62).pdf",
            "5. Remessa Betel.pdf",
            "5.1 Comprovante Remessa.pdf",
            "7. Relatório Mensal (S-30).pdf",
            "8. Extrato Bancário.pdf",
        )

        val checklist = MonthChecklistBuilder.build(congregacao, june)

        assertEquals(listOf("5.1 Comprovante Remessa.pdf"), checklist.filesFor("congregacao.comprovante_remessa"))
        assertEquals(listOf("5. Remessa Betel.pdf"), checklist.filesFor("congregacao.remessa_betel"))
        assertEquals(2, checklist.filesFor("congregacao.despesas").size)
        assertTrue(checklist.isPresent("congregacao.relatorio_mensal"))
        assertFalse(checklist.isPresent("congregacao.carta_agradecimento"))
        assertFalse(checklist.isPresent("congregacao.extrato_betel"))
        assertTrue(checklist.unmatchedFiles.isEmpty())
    }

    @Test
    fun `arquivos sem numero ficam separados`() {
        val august = listOf("Recibos agosto.pdf", "NU_150777600_02AGO2026_31AGO2026_assinado.pdf", "8. Extrato Bancário.pdf")

        val checklist = MonthChecklistBuilder.build(congregacao, august)

        assertEquals(listOf("NU_150777600_02AGO2026_31AGO2026_assinado.pdf", "Recibos agosto.pdf"), checklist.unmatchedFiles)
        assertTrue(checklist.isPresent("congregacao.extrato_bancario"))
    }

    @Test
    fun `10 outros nao e confundido com a categoria 1`() {
        val checklist = MonthChecklistBuilder.build(congregacao, listOf("10. Outros - a.pdf", "10.1 Outros - b.pdf"))

        assertEquals(2, checklist.filesFor("congregacao.outros").size)
        assertFalse(checklist.isPresent("congregacao.folha_de_contas"))
    }

    @Test
    fun `numeros sao da conta da pasta`() {
        val manutencao = DefaultCategories.all.filter { hierarchy.isAllowedIn(it, AccountType.MANUTENCAO) }

        val checklist = MonthChecklistBuilder.build(manutencao, listOf("2. Donativos.pdf", "9. Extrato Betel.pdf"))

        assertTrue(checklist.isPresent("manutencao.donativos_congregacoes"))
        assertEquals(listOf("9. Extrato Betel.pdf"), checklist.unmatchedFiles)
    }

    @Test
    fun `outros da manutencao e reconhecido pelo numero 6`() {
        val manutencao = DefaultCategories.all.filter { hierarchy.isAllowedIn(it, AccountType.MANUTENCAO) }

        val checklist = MonthChecklistBuilder.build(manutencao, listOf("6. Outros - a.pdf", "6.1 Outros - b.pdf", "1. Outros.pdf"))

        assertEquals(2, checklist.filesFor("manutencao.outros").size)
        assertTrue(checklist.isPresent("manutencao.folha_de_contas"))
    }
}
