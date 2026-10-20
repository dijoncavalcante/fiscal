package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.PageLayout
import com.bragadev.fiscal.domain.model.PageWord
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Total pela posição das palavras. Posições tiradas do OCR real (200 DPI) da NFC-e fotografada
 * "3.5 Despesa - Compra de refil para bebedouro": o título "TOTAL (R$)" fica em cima e o "50,00" embaixo, com o
 * "1" da coluna Itens no caminho; a coluna TOTAL da lista de itens saiu "60,00" (o OCR trocou 5 por 6).
 */
class TableTotalFinderTest {
    private fun word(text: String, x: Int, y: Int, width: Int = 15 * text.length, confidence: Float = 95f) =
        PageWord(text, x.toFloat(), y.toFloat(), width.toFloat(), 28f, confidence)

    private val itemsHeader = listOf(
        word("CÓDIGO", 185, 838), word("QT", 560, 838), word("UN", 610, 838), word("UNITÁRIO", 880, 838),
        word("TOTAL", 1225, 838, 103),
    )
    private val itemRow = listOf(
        word("FILTRO", 185, 900), word("1", 600, 900), word("UND", 640, 900), word("50,00", 1000, 900, 90),
        word("60,00", 1230, 900, 90),
    )
    private val totalsHeader = listOf(
        word("Itens", 185, 1047), word("Sub-Total", 330, 1047, 120), word("(R$)", 455, 1047, 50),
        word("Desconto", 600, 1047, 100), word("(R$)", 705, 1047, 50), word("Frete", 820, 1047, 60),
        word("(R$)", 885, 1047, 50), word("TOTAL", 1169, 1047, 96), word("(R$)", 1270, 1047, 50),
    )

    private fun totalsRow(subtotal: String = "50,00", total: String = "50,00", totalConfidence: Float = 94f) = listOf(
        word("1", 190, 1085, 15), word(subtotal, 390, 1085, 90), word("0,00", 640, 1085, 70),
        word("0,00", 850, 1085, 70), word(total, 1230, 1085, 100, totalConfidence),
    )

    private fun find(vararg rows: List<PageWord>, fromOcr: Boolean = true) =
        TableTotalFinder.find(PageLayout(0, rows.toList().flatten(), fromOcr))

    @Test
    fun `total embaixo do titulo da coluna, com a conta da tabela conferida`() {
        val found = find(itemsHeader, itemRow, totalsHeader, totalsRow())

        val total = found.single()
        assertEquals(BigDecimal("50.00"), total.value)
        assertEquals(TotalPlace.BELOW, total.place)
        assertEquals("TOTAL (R$)", total.label)
        assertEquals(94f, total.confidence)
        assertTrue(total.confirmedBySum)
    }

    @Test
    fun `coluna TOTAL da lista de itens nao e o total da compra`() {
        val found = find(itemsHeader, itemRow)

        assertTrue(found.isEmpty(), "achou: $found")
    }

    @Test
    fun `cabecalho da lista de itens quebrado em duas linhas tambem nao vale`() {
        // Cupom real "3.2 Compra de Torneira": "Código Descrição Qtde Un Vl Unit Valor" e, embaixo, "total".
        val header = listOf(word("Cédigo", 100, 456), word("Descrição", 250, 456), word("Valor", 1248, 456, 75))
        val secondLine = listOf(word("total", 1250, 490, 75))
        val item = listOf(word("1.00", 1004, 527), word("168,90", 1244, 534, 90))

        assertTrue(find(header, secondLine, item).isEmpty())
    }

    @Test
    fun `conta que nao fecha nao confirma o total`() {
        val total = find(totalsHeader, totalsRow(subtotal = "50,00", total = "60,00")).single()

        assertEquals(BigDecimal("60.00"), total.value)
        assertFalse(total.confirmedBySum)
    }

    @Test
    fun `confianca do OCR e a menor entre titulo e valor`() {
        val header = totalsHeader.map { if (it.text == "TOTAL") it.copy(confidence = 61f) else it }

        assertEquals(61f, find(header, totalsRow()).single().confidence)
        assertEquals(30f, find(totalsHeader, totalsRow(totalConfidence = 30f)).single().confidence)
    }

    @Test
    fun `valor a direita do titulo na mesma linha`() {
        val line = listOf(
            word("VALOR", 100, 500), word("A", 190, 500), word("PAGAR", 220, 500), word("R$", 320, 500),
            word("59,97", 380, 500)
        )

        val total = find(line).single()

        assertEquals(BigDecimal("59.97"), total.value)
        assertEquals(TotalPlace.RIGHT, total.place)
        assertTrue(total.isPayable)
    }

    @Test
    fun `totais de outra coisa nao valem`() {
        val lines = listOf(
            listOf(
                word("Qtde", 100, 100), word("total", 180, 100), word("de", 260, 100), word("itens", 300, 100),
                word("3,00", 400, 100)
            ),
            listOf(
                word("VALOR", 100, 200), word("TOTAL", 190, 200), word("DOS", 280, 200), word("PRODUTOS", 340, 200),
                word("213,09", 500, 200)
            ),
            listOf(
                word("Tributos", 100, 300), word("Totais", 230, 300), word("Incidentes", 330, 300),
                word("R$12,14", 500, 300)
            ),
        )

        assertTrue(find(*lines.toTypedArray()).isEmpty())
    }

    @Test
    fun `DANFE em texto - valor embaixo de VALOR TOTAL DA NOTA`() {
        val header = listOf(
            word("VALOR", 400, 600), word("DO", 470, 600), word("IPI", 500, 600),
            word("VALOR", 600, 600), word("TOTAL", 670, 600), word("DA", 750, 600), word("NOTA", 790, 600)
        )
        val values = listOf(word("0,00", 450, 640), word("232,00", 700, 640))

        val total = find(header, values, fromOcr = false).single()

        assertEquals(BigDecimal("232.00"), total.value)
        assertEquals("VALOR TOTAL DA NOTA", total.label)
        assertFalse(total.fromOcr)
    }

    @Test
    fun `palavras viram linhas na ordem da pagina`() {
        val texts = TableTotalFinder.lineTexts(PageLayout(0, totalsRow() + totalsHeader, true))

        assertEquals("Itens Sub-Total (R$) Desconto (R$) Frete (R$) TOTAL (R$)", texts[0])
        assertEquals("1 50,00 0,00 0,00 50,00", texts[1])
    }
}
