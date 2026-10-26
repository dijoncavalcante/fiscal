package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TO-62 (Registro de Transferência de Fundos) da Conta da Congregação, com os textos como o OCR lê os TO-62 reais
 * de 2025-2026 (todos escaneados) e como o PDFBox lê a "5. Remessa Betel" (impressão do site de donativos).
 */
class TransferRecordRulesTest {
    private val requirements = TransferRecordRules.Requirements(accountsServant = DIJON, expenseSigner = ANDREY)

    /** "4. TO-62.pdf" de agosto/2026 pelo OCR (só as linhas que importam; o resto são as instruções do formulário). */
    private val augustOcr = """
        16/09/2026, 19:09 Registro de Transferéncla de Fundos (TO-62) :: Contas :; JW Hub
        REGISTRO DE TRANSFERENCIA DE FUNDOS (TO-62)
        Congregac¢ao: Trinta e Um de Marco - Manaus AM (68361)
        INSTRUCOES
        Donativos e pagamentos
        Obra mundial (caixas de donativos) 395,00
        Obra mundial (resolucgao) 580,00
        Total de donativos e pagamentos: 975,00
        Enviar fundos para depésito em Betel  aawe  S. O ne
        TOTAL DE FUNDOS ENVIADOS: 975,00
        Referéncia bancaria de pagamento ou transferéncia (se for 0 caso):
        CONFIRMAGAO DE ENVIO NO JW HUB (SE FOR O CASO)
        30 de agosto de 2026 3290248
        ((reénchido por) CZ esrovado por)
    """.trimIndent()

    /** "5. Remessa Betel.pdf" de agosto/2026 (PDF com texto). */
    private val augustRemittance = DocumentContent(
        listOf(
            """
            Total R$975,00
            Remessas
            Associação Torre de Vigia de Bíblias e Tratados
            Donativos Valor
            Obra mundial (das caixas de donativos) 395,00
            Obra mundial (resolução) 580,00
            Enviado em: 31 de agosto de 2026
            """.trimIndent(),
        ),
    )

    private fun remittance(total: String?) =
        TransferRecordRules.Remittance(listOf("5. Remessa Betel.pdf"), total?.let(::BigDecimal))

    private fun signed(text: String = augustOcr, ocr: Boolean = true) = DocumentContent(
        listOf(text, "https://hub.jw.org/field-accounting/ 2/2"),
        signers = listOf("CN=DIJON BRAGA:12345678900", "CN=ANDREY ALVES DA COSTA:98765432100"),
        ocrPages = if (ocr) setOf(0, 1) else emptySet(),
    )

    private fun check(content: DocumentContent, remittance: TransferRecordRules.Remittance = remittance("975.00")) =
        TransferRecordRules.check(content, requirements, remittance)

    @Test
    fun `TO-62 assinado pelos dois e com o total da remessa passa`() {
        val result = check(signed())
        assertTrue(result.check.isValid)
        assertNull(result.note)
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS R$ 975,00, igual ao da Remessa Betel; assinado por $DIJON e $ANDREY",
            result.check.amountEvidence,
        )
    }

    @Test
    fun `total da remessa e lido do PDF do site`() {
        assertEquals(BigDecimal("975.00"), TransferRecordRules.remittanceTotal(augustRemittance))
        assertEquals(
            BigDecimal("1142.00"),
            TransferRecordRules.remittanceTotal(DocumentContent(listOf("Total R$1.142,00\nRemessas"))),
        )
        assertNull(TransferRecordRules.remittanceTotal(DocumentContent(listOf("Remessas Donativos Valor 395,00"))))
    }

    @Test
    fun `total de fundos enviados como o OCR le os TO-62 reais`() {
        assertEquals(BigDecimal("975.00"), TransferRecordRules.fundsSent(augustOcr))
        // Setembro/2025: ponto no lugar da vírgula.
        assertEquals(BigDecimal("791.50"), TransferRecordRules.fundsSent("TOTAL DE FUNDOS ENVIADOS: 791.50"))
        // Maio/2026: a vírgula dos centavos sumiu ("1.132,25").
        assertEquals(BigDecimal("1132.25"), TransferRecordRules.fundsSent("TOTAL DE FUNDOS ENVIADOS: 1.13225"))
        assertEquals(BigDecimal("825.00"), TransferRecordRules.fundsSent(": TOTAL DE FUNDOS ENVIADOS: 825,00"))
        // "Total de donativos e pagamentos" não é o campo pedido.
        assertNull(TransferRecordRules.fundsSent("Total de donativos e pagamentos: 975,00"))
    }

    @Test
    fun `assinatura a mao nao vale e falta das duas assinaturas vira pendencia`() {
        val result = check(signed().copy(signers = emptyList()))
        assertEquals(setOf(ExpenseRequirement.SIGNATURE), result.check.missing)
        assertEquals("Falta assinatura digital (gov.br) de $DIJON e $ANDREY", result.note)
        assertTrue(TransferRecordRules.isAutomaticNote(result.note!!))

        val onlyAndrey = check(signed().copy(signers = listOf("CN=ANDREY ALVES DA COSTA")))
        assertEquals("Falta assinatura digital (gov.br) de $DIJON", onlyAndrey.note)
    }

    @Test
    fun `servo de contas nao configurado diz onde configurar`() {
        val result = TransferRecordRules.check(
            signed(),
            requirements.copy(accountsServant = null),
            remittance("975.00"),
        )
        assertEquals(
            "Falta assinatura digital (gov.br) do servo de contas " +
                    "(configure em Configurações → Assinaturas das despesas e extratos)",
            result.note,
        )
        assertTrue(TransferRecordRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `total diferente da remessa vira pendencia com os dois valores`() {
        val photo = check(signed(), remittance("960.00"))
        assertEquals(setOf(ExpenseRequirement.TRANSFER_TOTAL), photo.check.missing)
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS do TO-62 (R$ 975,00) é diferente do Total da Remessa Betel (R$ 960,00): " +
                    "confira à mão (foto)",
            photo.note,
        )
        val text = check(signed(ocr = false), remittance("960.00"))
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS do TO-62 (R$ 975,00) é diferente do Total da Remessa Betel (R$ 960,00): " +
                    "corrija o documento errado",
            text.note,
        )
        listOf(photo.note!!, text.note!!).forEach { assertTrue(TransferRecordRules.isAutomaticNote(it)) }
    }

    @Test
    fun `sem documento na Remessa Betel vira pendencia`() {
        val result = check(signed(), TransferRecordRules.Remittance(emptyList(), null))
        assertEquals(setOf(ExpenseRequirement.TRANSFER_TOTAL), result.check.missing)
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS do TO-62 (R$ 975,00) não pôde ser conferido: \"5. Remessa Betel\" não tem " +
                    "nenhum documento neste mês",
            result.note,
        )
        assertTrue(TransferRecordRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `valor nao lido pede para conferir a mao`() {
        val sentUnread =
            check(signed(augustOcr.replace("TOTAL DE FUNDOS ENVIADOS: 975,00", "TOTAL DE FUNDOS ENVIADOS:")))
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS do TO-62 não foi lido: confira à mão se é igual ao Total da Remessa Betel " +
                    "(R$ 975,00)",
            sentUnread.note,
        )
        val totalUnread = check(signed(), remittance(null))
        assertEquals(
            "Total da Remessa Betel não foi lido: confira à mão se é igual ao TOTAL DE FUNDOS ENVIADOS do TO-62 " +
                    "(R$ 975,00)",
            totalUnread.note,
        )
        listOf(sentUnread.note!!, totalUnread.note!!).forEach { assertTrue(TransferRecordRules.isAutomaticNote(it)) }
    }

    @Test
    fun `documento sem o titulo ou de outra congregacao`() {
        val other = signed(augustOcr.replace("Trinta e Um de Marco - Manaus AM (68361)", "Japiim - Manaus AM (12345)"))
        val result = check(other)
        assertEquals(setOf(ExpenseRequirement.TRANSFER_HEADER), result.check.missing)
        assertEquals(
            "TO-62 sem a congregação \"Trinta e Um de Março - Manaus AM (68361)\" na foto: confira à mão",
            result.note,
        )

        val notTo62 = check(signed("Folha de Contas TOTAL DE FUNDOS ENVIADOS: 975,00", ocr = false))
        assertEquals(
            "TO-62 sem \"REGISTRO DE TRANSFERÊNCIA DE FUNDOS (TO-62)\": troque pelo TO-62 certo; " +
                    "TO-62 sem a congregação \"Trinta e Um de Março - Manaus AM (68361)\": troque pelo TO-62 certo",
            notTo62.note,
        )
        listOf(result.note!!, notTo62.note!!).forEach { assertTrue(TransferRecordRules.isAutomaticNote(it)) }
    }

    @Test
    fun `selo gov br completo no texto tambem vale como assinatura`() {
        val stamped = augustOcr + "\nDocumento assinado digitalmente DIJON BRAGA Data: 17/09/2026 " +
                "Verifique em https://validar.iti.gov.br\nDocumento assinado digitalmente ANDREY ALVES DA COSTA " +
                "Data: 17/09/2026 Verifique em https://validar.iti.gov.br"
        assertTrue(check(signed(stamped).copy(signers = emptyList())).check.isValid)
    }

    // ---- 5. Remessa Betel ----

    /** O resto da "5. Remessa Betel.pdf" de agosto/2026, com a confirmação do site. */
    private fun remittancePage(done: String = "Movimentação realizada.", total: String = "975,00") = DocumentContent(
        listOf(
            augustRemittance.pageTexts.single().replace("975,00", total) +
                    "\nNúmero de confirmação: 3290248\nFilipe Costa\nTrinta e Um de Março - Manaus AM (68361)\n$done\n" +
                    "Detalhes\nData do depósito\n31 de agosto de 2026",
        ),
    )

    private val to62Sent = TransferRecordRules.TransferRecord(listOf("4. TO-62.pdf"), BigDecimal("975.00"), true)

    @Test
    fun `remessa com Movimentacao realizada e o total do TO-62 passa`() {
        val result = TransferRecordRules.checkRemittance(remittancePage(), to62Sent)
        assertTrue(result.check.isValid)
        assertNull(result.note)
        assertEquals(
            "Total R$ 975,00, igual ao TOTAL DE FUNDOS ENVIADOS do TO-62; Movimentação realizada.",
            result.check.amountEvidence,
        )
    }

    @Test
    fun `Transacao realizada das remessas ate julho de 2026 tambem vale`() {
        assertTrue(TransferRecordRules.checkRemittance(remittancePage("Transação realizada."), to62Sent).check.isValid)
    }

    @Test
    fun `remessa sem os textos do site vira pendencia`() {
        val result = TransferRecordRules.checkRemittance(
            DocumentContent(listOf("Comprovante de transferência Total R$975,00")),
            to62Sent,
        )
        assertEquals(setOf(ExpenseRequirement.REMITTANCE_TEXT), result.check.missing)
        assertEquals(
            "Remessa Betel sem \"Remessas\": troque pela remessa certa (impressão do site de donativos); " +
                    "Remessa Betel sem \"Movimentação realizada.\": troque pela remessa certa (impressão do site de " +
                    "donativos)",
            result.note,
        )
        assertTrue(TransferRecordRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `total da remessa diferente do TO-62 vira pendencia com os dois valores`() {
        val photoTo62 = TransferRecordRules.checkRemittance(remittancePage(total = "960,00"), to62Sent)
        assertEquals(setOf(ExpenseRequirement.TRANSFER_TOTAL), photoTo62.check.missing)
        assertEquals(
            "Total da Remessa Betel (R$ 960,00) é diferente do TOTAL DE FUNDOS ENVIADOS do TO-62 (R$ 975,00): " +
                    "confira à mão (foto)",
            photoTo62.note,
        )
        val textTo62 =
            TransferRecordRules.checkRemittance(remittancePage(total = "960,00"), to62Sent.copy(fromOcr = false))
        assertEquals(
            "Total da Remessa Betel (R$ 960,00) é diferente do TOTAL DE FUNDOS ENVIADOS do TO-62 (R$ 975,00): " +
                    "corrija o documento errado",
            textTo62.note,
        )
        listOf(photoTo62.note!!, textTo62.note!!).forEach { assertTrue(TransferRecordRules.isAutomaticNote(it)) }
    }

    @Test
    fun `sem documento no TO-62 a remessa ganha pendencia`() {
        val result = TransferRecordRules.checkRemittance(
            remittancePage(),
            TransferRecordRules.TransferRecord(emptyList(), null, fromOcr = false),
        )
        assertEquals(
            "Total da Remessa Betel (R$ 975,00) não pôde ser conferido: \"4. TO-62\" não tem nenhum documento neste mês",
            result.note,
        )
        assertTrue(TransferRecordRules.isAutomaticNote(result.note!!))

        val unread = TransferRecordRules.checkRemittance(remittancePage(), to62Sent.copy(sent = null))
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS do TO-62 não foi lido: confira à mão se é igual ao Total da Remessa Betel " +
                    "(R$ 975,00)",
            unread.note,
        )
    }

    private companion object {
        const val DIJON = "Dijon Braga"
        const val ANDREY = "Andrey Alves da Costa"
    }
}
