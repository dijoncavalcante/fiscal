package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import org.junit.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "1. Folha de Contas" (S-26), com os textos como o PDFBox e o OCR leem as folhas reais de 2025-2026. */
class AccountSheetRulesTest {
    private val september = YearMonth.of(2026, 9)

    /** Impressão do JW Hub da Conta da Manutenção. */
    private val manutencao = DocumentContent(
        listOf(
            """
            FOLHA DE CONTAS (S-26)
            Comissão de Funcionamento do Salão do Reino setembro de 2026
            ENTRADAS CONTA
            DATA DESCRIÇÃO DA MOVIMENTAÇÃO S ENTRADA SAÍDA ENTRADA SAÍDA
            10 Donativo eletrônico - RESOLUÇÃO MENSAL CONG. 31 DE MARÇO CE 800,00
            16/10/2026, 19:19 Folha de Contas (S-26) :: Contas :: JW Hub
            """.trimIndent(),
        ),
    )

    /** Impressão do JW Hub da Conta da Congregação: cita "COMISSÃO DE FUNCIONAMENTO" numa saída. */
    private val congregacao = DocumentContent(
        listOf(
            """
            FOLHA DE CONTAS (S-26)
            Trinta e Um de Março - Manaus AM (68361) setembro de 2026
            395,00
            ENTRADAS CONTA
            10
            RESOLUÇÃO MENSAL COMISSÃO DE
            FUNCIONAMENTO
            G 800,00
            """.trimIndent(),
        ),
    )

    @Test
    fun `folha certa de cada conta, do mes em edicao`() {
        val m = AccountSheetRules.check(manutencao, AccountType.MANUTENCAO, september)
        assertTrue(m.check.isValid, m.note)
        assertEquals(
            "FOLHA DE CONTAS (S-26) de Comissão de Funcionamento do Salão do Reino, setembro de 2026",
            m.check.amountEvidence,
        )
        val c = AccountSheetRules.check(congregacao, AccountType.CONGREGACAO, september)
        assertTrue(c.check.isValid, c.note)
    }

    @Test
    fun `folha da outra conta pede a troca (caso real de junho de 2026 na Congregacao)`() {
        val result = AccountSheetRules.check(manutencao, AccountType.CONGREGACAO, september)
        assertEquals(setOf(ExpenseRequirement.SHEET_HEADER), result.check.missing)
        assertEquals(
            "Folha de Contas da conta da Manutenção (Comissão de Funcionamento do Salão do Reino), não da conta da " +
                    "Congregação: troque pela folha certa",
            result.note,
        )
        assertTrue(AccountSheetRules.isAutomaticNote(result.note!!))
        // A da Congregação cita "COMISSÃO DE FUNCIONAMENTO", mas sem o nome completo não vale para a Manutenção.
        val other = AccountSheetRules.check(congregacao, AccountType.MANUTENCAO, september)
        assertTrue(other.note!!.startsWith("Folha de Contas da conta da Congregação (Trinta e Um de Março"))
    }

    @Test
    fun `folha de outro mes pede a folha do mes`() {
        val result = AccountSheetRules.check(manutencao, AccountType.MANUTENCAO, YearMonth.of(2026, 10))
        assertEquals(setOf(ExpenseRequirement.SHEET_MONTH), result.check.missing)
        assertEquals(
            "Folha de Contas de setembro de 2026, mas o mês em edição é outubro de 2026: troque pela folha do mês",
            result.note,
        )
        assertTrue(AccountSheetRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `sem o titulo S-26 nao e Folha de Contas`() {
        // O rodapé do JW Hub também diz "Folha de Contas (S-26)": sai junto.
        val content = DocumentContent(
            listOf(
                manutencao.pageTexts[0].replace("FOLHA DE CONTAS (S-26)", "RELATÓRIO").substringBefore("16/10/2026")
            ),
        )
        val result = AccountSheetRules.check(content, AccountType.MANUTENCAO, september)
        assertEquals("Folha de Contas sem \"FOLHA DE CONTAS (S-26)\": troque pela folha certa", result.note)
        assertTrue(AccountSheetRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `digitalizada - o que nao foi lido pede para conferir a mao`() {
        val scanned =
            DocumentContent(listOf("FOLHA DE CONTAS (S-26)\nTrinta e Um de Margo - Manaus AM\n"), ocrPages = setOf(0))
        val result = AccountSheetRules.check(scanned, AccountType.CONGREGACAO, september)
        assertEquals(
            "Cabeçalho da Folha de Contas não lido na foto (não li \"Trinta e Um de Março - Manaus AM (68361)\"): " +
                    "confira à mão; Mês da Folha de Contas não foi lido: confira à mão se é de setembro de 2026",
            result.note,
        )
        assertTrue(AccountSheetRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `mes lido logo depois do nome da conta, tambem com erros do OCR`() {
        val headers = listOf("Trinta e Um de Março - Manaus AM (68361)")
        // "Trinta e Um de Março" não é data; "JULHO" não vira "JUNHO".
        assertEquals(
            YearMonth.of(2026, 7),
            AccountSheetRules.findMonth(
                "FOLHA DE CONTAS (S-26) Trinta e Um de Março - Manaus AM (68361) julho de 2026",
                headers
            ),
        )
        assertEquals(
            YearMonth.of(2026, 3),
            AccountSheetRules.findMonth("FOLHA DE CONTAS (S-26) xx margo de 2026", headers)
        )
        assertNull(AccountSheetRules.findMonth("FOLHA DE CONTAS (S-26) Trinta e Um de Março - Manaus AM", headers))
    }

    @Test
    fun `pendencia do usuario nao e automatica e so a folha das contas e conferida`() {
        assertFalse(AccountSheetRules.isAutomaticNote("Falta a assinatura do secretário"))
        assertEquals("congregacao.folha_de_contas", AccountSheetRules.categoryFor(AccountType.CONGREGACAO))
        assertEquals("manutencao.folha_de_contas", AccountSheetRules.categoryFor(AccountType.MANUTENCAO))
        assertNull(AccountSheetRules.categoryFor(null))
    }
}
