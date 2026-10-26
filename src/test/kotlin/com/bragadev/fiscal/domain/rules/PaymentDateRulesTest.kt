package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import com.bragadev.fiscal.domain.model.ExpenseRequirements
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Data da transferência das despesas ("3. Despesas" das duas contas) no mês em edição, com os textos como o PDFBox
 * e o OCR leem os comprovantes reais de 2025-2026 (next/Bradesco e Nubank).
 */
class PaymentDateRulesTest {
    private val september = YearMonth.of(2026, 9)
    private val requirements = ExpenseRequirements(signerName = null, exemptBy = "Resolução", month = september)

    /** Comprovante do next ("3. Despesa - Conta de energia.pdf" de setembro/2026), página com texto. */
    private fun next(date: String) = """
        Comprovante de transação Pix
        Data da operação: $date 19:42:21
        Valor: R$ 210,40
        Id da transação: E60746948202609182242M39436OzP8A
        Data: $date
    """.trimIndent()

    /** Nubank pelo OCR, com o selo gov.br assinado em outro dia ("Data: … -0300" é a assinatura). */
    private val nubankWithStamp = """
        Comprovante de transferéncia
        15 SET 2026 - 16:56:42
        Valor R$ 1.000,00
        Tipo de transferéncia Pix
        Documento assinado digitalmente
        g N. Data: 02/10/2026 17:22:26-0300
        Verifique em https://validar.iti.gov.br
    """.trimIndent()

    private fun check(vararg pages: String, ocrPages: Set<Int> = emptySet()) =
        ExpenseDocumentRules.check(DocumentContent(pages.toList(), ocrPages = ocrPages), requirements)

    @Test
    fun `transferencia do mes em edicao nao gera pendencia`() {
        assertNull(check(next("18/09/2026")).paymentDate)
        // A data do selo gov.br (02/10) é a da assinatura, não a da transferência.
        assertNull(check(nubankWithStamp, ocrPages = setOf(0)).paymentDate)
    }

    @Test
    fun `transferencia de outro mes no PDF com texto pede para conferir a despesa`() {
        val result = check(next("01/10/2026"))
        assertTrue(ExpenseRequirement.PAYMENT_MONTH in result.missing)
        assertFalse(result.isValid)
        val note = ExpenseDocumentRules.issueNote(result.copy(missing = result.missing - ExpenseRequirement.SIGNATURE))
        assertEquals(
            "Comprovante Pix/TED de 01/10/2026, mas o mês em edição é setembro de 2026: confira se a despesa é deste mês",
            note,
        )
        assertTrue(ExpenseDocumentRules.isAutomaticNote(note!!))
    }

    @Test
    fun `foto de outro mes ou data nao lida pedem para conferir a mao`() {
        val photo = check(nubankWithStamp.replace("15 SET 2026", "29 AGO 2026"), ocrPages = setOf(0))
        assertEquals(LocalDate.of(2026, 8, 29), photo.paymentDate?.found)
        val photoNote = ExpenseDocumentRules.issueNote(photo.copy(missing = emptySet()))
        assertEquals(
            "Comprovante Pix/TED de 29/08/2026, mas o mês em edição é setembro de 2026: confira à mão (foto)",
            photoNote,
        )

        // Só o selo e a expiração do QR Code: nenhuma data da transferência.
        val unread = check(
            "Comprovante de transferência Pix Valor R$ 82,00 Data: 01/09/2026 15:49:37-0300 Expiração 02/09/2026 11:53:54",
            ocrPages = setOf(0),
        )
        val unreadNote = ExpenseDocumentRules.issueNote(unread.copy(missing = emptySet()))
        assertEquals(
            "Data da transferência do comprovante Pix/TED não foi lida: confira à mão se é de setembro de 2026",
            unreadNote,
        )
        listOf(photoNote!!, unreadNote!!).forEach { assertTrue(ExpenseDocumentRules.isAutomaticNote(it)) }
    }

    @Test
    fun `todos os comprovantes do arquivo precisam ser do mes`() {
        val result = check(next("18/09/2026"), next("28/08/2026"))
        assertEquals(LocalDate.of(2026, 8, 28), result.paymentDate?.found)
    }

    @Test
    fun `sem mes em edicao ou sem comprovante a data nao e conferida`() {
        val noMonth =
            ExpenseDocumentRules.check(DocumentContent(listOf(next("01/10/2026"))), requirements.copy(month = null))
        assertNull(noMonth.paymentDate)
        assertNull(check("CUPOM FISCAL TOTAL R$ 10,00 Data: 01/10/2026").paymentDate)
    }

    @Test
    fun `datas como os bancos escrevem`() {
        fun date(text: String) = TransferDates.find(text)?.date
        assertEquals(LocalDate.of(2026, 4, 24), date("Data da operacio: 24/04/2026 19:56:59"))
        assertEquals(LocalDate.of(2025, 11, 1), date("Data do pagamento Sabado, 01/11/2025"))
        // next pelo OCR, sem as barras ("3.1 Despesa - Compra de material para pintura", julho/2026).
        assertEquals(LocalDate.of(2026, 7, 18), date("wees 1 4 Date da operaeha: 18072026 - 13:03"))
        assertEquals(LocalDate.of(2026, 4, 10), date("10 ABR 2026 12.44.35"))
        assertEquals(LocalDate.of(2026, 3, 19), date("19 MAR 2026 1A:30:08"))
        // Só o identificador do Pix (UTC): 11/12/2025 13:13 UTC = 10:13 em Brasília.
        val fromId = TransferDates.find("ID da transação: E18236120202512111313s19451aa71f")
        assertEquals(LocalDate.of(2025, 12, 11), fromId?.date)
        assertTrue(fromId!!.fromPixId)
        // 01/10 às 01:00 UTC ainda é 30/09 no Brasil.
        assertEquals(LocalDate.of(2026, 9, 30), date("E18236120202610010100s19451aa71f"))
        assertNull(date("Data: 12/08/2026 07:38:13-0300"))
        assertNull(date("Expiragao 17/08/2026 11:59:23"))
    }
}
