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

/**
 * "Relatório Mensal" (S-30 / S-44), com os textos como o PDFBox e o OCR leem os relatórios reais de 2025-2026.
 * Nos 24 relatórios reais desse ano (5 digitalizados) nenhum fica com pendência.
 */
class MonthlyReportRulesTest {
    private val september = YearMonth.of(2026, 9)

    /** Impressão do JW Hub da Conta da Congregação (S-30). */
    private val congregacao = DocumentContent(
        listOf(
            """
            RELATÓRIO MENSAL DE CONTAS DA CONGREGAÇÃO (S-30)
            Instruções: Este relatório deve ser preenchido pelo servo de contas e verificado pelo secretário antes da segunda reunião do
            meio de semana de cada mês. O servo de contas deve arquivar o relatório e a Folha de Contas (S-26) correspondente junto com os arquivos do
            mês corrente.
            Trinta e Um de Março - Manaus AM (68361) setembro de 2026
            Saldo disponível no começo do mês 1.283,79
            """.trimIndent(),
        ),
    )

    /** Impressão do JW Hub da Conta da Manutenção (S-44): o título quebra a linha e o mês vem sozinho. */
    private val manutencao = DocumentContent(
        listOf(
            """
            RELATÓRIO MENSAL DE CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO
            REINO (S-44)
            Instruções: Este relatório deve ser preenchido pelo servo de contas e verificado pelo secretário da congregação de contato.
            O servo de contas deve arquivar o relatório e a Folha de Contas (S-26) correspondente junto com os arquivos do mês corrente.
            setembro de 2026
            Saldo disponível no começo do mês 2.122,69
            """.trimIndent(),
        ),
    )

    /** Digitalizado (OCR), como "7. Relatório Mensal (S-30).pdf" de novembro/2025: sem acentos, "Margo". */
    private val congregacaoOcr = DocumentContent(
        listOf(
            """
            05/12/2025, 16:58 Relatério mensal de contas da congregacaéo (S-30) :: Contas :: JW Hub
            RELATORIO MENSAL DE CONTAS DA CONGREGACAO (S-30)
            Instrug6es: Este relatorio deve ser preenchido pelo servo de contas e verificado pelo secretario
            Trinta e Um de Margo - Manaus AM (68361) setembro de 2026
            """.trimIndent(),
        ),
        ocrPages = setOf(0),
    )

    @Test
    fun `relatorio certo de cada conta, do mes em edicao`() {
        val c = MonthlyReportRules.check(congregacao, AccountType.CONGREGACAO, september)
        assertTrue(c.check.isValid, c.note)
        assertEquals("RELATÓRIO MENSAL DE CONTAS DA CONGREGAÇÃO (S-30), setembro de 2026", c.check.amountEvidence)

        val m = MonthlyReportRules.check(manutencao, AccountType.MANUTENCAO, september)
        assertTrue(m.check.isValid, m.note)
        assertEquals(
            "RELATÓRIO MENSAL DE CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO REINO (S-44), setembro de 2026",
            m.check.amountEvidence,
        )

        val ocr = MonthlyReportRules.check(congregacaoOcr, AccountType.CONGREGACAO, september)
        assertTrue(ocr.check.isValid, ocr.note)
    }

    @Test
    fun `relatorio da outra conta pede a troca`() {
        val result = MonthlyReportRules.check(manutencao, AccountType.CONGREGACAO, september)
        assertEquals(setOf(ExpenseRequirement.REPORT_TITLE), result.check.missing)
        assertEquals(
            "Relatório Mensal da conta da Manutenção (S-44), não da conta da Congregação (S-30): troque pelo relatório certo",
            result.note,
        )
        assertTrue(MonthlyReportRules.isAutomaticNote(result.note!!))

        val other = MonthlyReportRules.check(congregacao, AccountType.MANUTENCAO, september)
        assertEquals(
            "Relatório Mensal da conta da Congregação (S-30), não da conta da Manutenção (S-44): troque pelo relatório certo",
            other.note,
        )
    }

    @Test
    fun `relatorio de outro mes pede o relatorio do mes`() {
        val result = MonthlyReportRules.check(congregacao, AccountType.CONGREGACAO, YearMonth.of(2026, 10))
        assertEquals(setOf(ExpenseRequirement.REPORT_MONTH), result.check.missing)
        assertEquals(
            "Relatório Mensal de setembro de 2026, mas o mês em edição é outubro de 2026: troque pelo relatório do mês",
            result.note,
        )
        assertTrue(MonthlyReportRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `outro documento na categoria pede o relatorio certo`() {
        val sheet =
            DocumentContent(listOf("FOLHA DE CONTAS (S-26)\nComissão de Funcionamento do Salão do Reino setembro de 2026"))
        val result = MonthlyReportRules.check(sheet, AccountType.MANUTENCAO, september)
        assertEquals(setOf(ExpenseRequirement.REPORT_TITLE), result.check.missing)
        assertEquals(
            "Relatório Mensal sem \"RELATÓRIO MENSAL DE CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO REINO (S-44)\": " +
                    "troque pelo relatório certo",
            result.note,
        )
        assertTrue(MonthlyReportRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `digitalizado nunca pede a troca`() {
        val unreadable = DocumentContent(listOf("RELAT0RI0 MENS4L ... borrado"), ocrPages = setOf(0))
        val result = MonthlyReportRules.check(unreadable, AccountType.CONGREGACAO, september)
        assertEquals(
            "Título do Relatório Mensal não lido na foto (não li \"RELATÓRIO MENSAL DE CONTAS DA CONGREGAÇÃO (S-30)\"): " +
                    "confira à mão; Mês do Relatório Mensal não foi lido: confira à mão se é de setembro de 2026",
            result.note,
        )
        assertTrue(MonthlyReportRules.isAutomaticNote(result.note!!))

        val otherMonth = MonthlyReportRules.check(congregacaoOcr, AccountType.CONGREGACAO, YearMonth.of(2026, 10))
        assertEquals(
            "Relatório Mensal de setembro de 2026, mas o mês em edição é outubro de 2026: confira à mão (foto)",
            otherMonth.note,
        )
    }

    @Test
    fun `mes do relatorio ignora a data da impressao e o nome da congregacao`() {
        assertEquals(september, MonthlyReportRules.findMonth(congregacaoOcr.pageTexts.single()))
        assertNull(MonthlyReportRules.findMonth("Trinta e Um de Março - Manaus AM (68361) 05/12/2025"))
    }

    @Test
    fun `pendencia do usuario nao e automatica`() {
        assertFalse(MonthlyReportRules.isAutomaticNote("Falta a assinatura do secretário"))
    }
}
