package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AmountMismatch
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Textos tirados das despesas reais da Conta da Manutenção (PDF e OCR de fotos). */
class ExpenseDocumentRulesTest {
    private val cupomOcr = "COMPROVANTE DE VENDA 46.182.254 ANDRESSA DOS SANTOS FERREIRA CNPU: AG 182.254/0001-79 " +
            "VENDA 36791 | 76 DRENO 5x R$ 25,00 RS 425,00 Valor bruto R$ 125,00 Desconto na venda R$ 0,00 " +
            "LIQUIDO TOTAL OO. R$ 425,00"
    private val pixToStore = "Comprovante de transação Pix Pagamento Data da operação: 19/09/2026 10:55:34 " +
            "Dados do recebedor Nome: 46.182.254 ANDRESSA DOS SANTOS FERREIRA Valor: 125.00 " +
            "Id da transação: E60746948202609191355M3943eq0hGo"
    private val pixToElves = "Comprovante de transação Pix Transferência Data da operação: 05/08/2026 - 15:38 " +
            "Dados do recebedor Nome: Elves Lincoln Menezes de Lima CPF/CNPJ: ***.451.352-** " +
            "Descrição: Pix para Elves Lima - lâmpadas"
    private val elvesCertificate = "CN=ELVES LINCOLN MENEZES DE LIMA"

    private fun check(vararg pages: String, signers: List<String> = listOf(elvesCertificate)) =
        ExpenseDocumentRules.check(DocumentContent(pages.toList(), signers))

    @Test
    fun `cupom, comprovante Pix e assinatura no certificado = despesa verificada`() {
        val result = check(cupomOcr, pixToStore)

        assertTrue(result.isValid, "faltou: ${result.missing}")
    }

    @Test
    fun `nome do Elves so como recebedor do Pix nao conta como assinatura`() {
        val result = check(cupomOcr, pixToElves, signers = emptyList())

        assertEquals(setOf(SIGNATURE), result.missing)
    }

    @Test
    fun `selo do gov br no texto vale como assinatura, qualquer que seja a data`() {
        val stamp = "Documento assinado digitalmente ELVES LINCOLN MENEZES DE LIMA Data: 21/08/2026 16:16:37-0300 " +
                "Verifique em https://validar. iti. gov.br"
        val otherDate = stamp.replace("21/08/2026 16:16:37", "03/01/2027 09:00:00")

        assertTrue(check(cupomOcr + " " + stamp, pixToStore, signers = emptyList()).isValid)
        assertTrue(check(cupomOcr, pixToStore + " " + otherDate, signers = emptyList()).isValid)
    }

    @Test
    fun `assinatura de outra pessoa nao vale`() {
        val stamp =
            "Documento assinado digitalmente FULANO DE TAL Data: 21/08/2026 Verifique em https://validar.iti.gov.br"

        assertEquals(
            setOf(SIGNATURE),
            check(cupomOcr + " " + stamp, pixToStore, signers = listOf("CN=FULANO DE TAL")).missing
        )
    }

    @Test
    fun `orcamento nao e cupom nem nota fiscal`() {
        val budget = "MKL Comércio e Serviços Orçamento 007-2026 Cliente: Andrei Alves Materiais Descrição Unidade " +
                "Preço unitário Qtd. Caixa de copo 180ml R$ 108,00"

        assertEquals(setOf(FISCAL_DOCUMENT), check(budget, pixToStore).missing)
    }

    @Test
    fun `nota fiscal lida com erros de OCR ainda e reconhecida`() {
        // OCR de foto de conta de luz: "Documento Auxiliar da Nota Fiscal" e "Chave de Acesso" com letras trocadas.
        val bill = "Amazonas Energia [a] Documents Aus a Nata Fecal Frergia Coneulte pein Chave de Aceesn"

        assertTrue(check(bill, pixToStore).isValid)
        assertTrue(check("DANFE Documento Auxiliar da Nota Fiscal Eletronica NF-e", pixToStore).isValid)
        assertTrue(check("CUPOM FISCAL ELETRONICO - SAT", pixToStore).isValid)
    }

    @Test
    fun `fatura que so oferece pagar com Pix nao e comprovante`() {
        val bill = "NOTA FISCAL FATURA DE SERVIÇOS Pague com Pix: aponte a câmera para o QR Code. Vencimento 10/09/2026"

        assertEquals(setOf(PAYMENT_PROOF), check(bill).missing)
    }

    @Test
    fun `comprovante de TED e identificador do Pix contam como pagamento`() {
        assertTrue(check(cupomOcr, "COMPROVANTE DE TRANSFERÊNCIA TED Valor R$ 125,00").isValid)
        assertTrue(check(cupomOcr, "Pagamento efetuado E60746948202607151342M3943amS3S8").isValid)
    }

    @Test
    fun `pagina em branco nao tem nada`() {
        assertEquals(setOf(FISCAL_DOCUMENT, PAYMENT_PROOF, SIGNATURE), check("", signers = emptyList()).missing)
    }

    @Test
    fun `pendencia diz em poucas palavras o que falta`() {
        assertNull(ExpenseDocumentRules.issueNote(ExpenseCheck()))
        assertEquals(
            "Falta assinatura de Elves Lincoln",
            ExpenseDocumentRules.issueNote(ExpenseCheck(setOf(SIGNATURE)))
        )
        assertEquals(
            "Falta cupom ou nota fiscal e assinatura de Elves Lincoln",
            ExpenseDocumentRules.issueNote(ExpenseCheck(setOf(SIGNATURE, FISCAL_DOCUMENT))),
        )
        assertEquals(
            "Falta cupom ou nota fiscal, comprovante Pix/TED e assinatura de Elves Lincoln",
            ExpenseDocumentRules.issueNote(ExpenseCheck(setOf(FISCAL_DOCUMENT, PAYMENT_PROOF, SIGNATURE))),
        )
        assertEquals("Não foi possível ler o PDF", ExpenseDocumentRules.issueNote(ExpenseCheck(unreadable = true)))
    }

    @Test
    fun `so pendencias escritas pela conferencia sao automaticas`() {
        assertTrue(ExpenseDocumentRules.isAutomaticNote("Falta comprovante Pix/TED"))
        assertTrue(ExpenseDocumentRules.isAutomaticNote("Não foi possível ler o PDF"))
        assertFalse(ExpenseDocumentRules.isAutomaticNote("Arquivo errado — trocar"))
        assertFalse(ExpenseDocumentRules.isAutomaticNote("Falta comprovante Pix/TED e o recibo"))
    }

    // ---- Valor do comprovante × cupom/nota ----

    /** Orçamento/recibo real: "Total R$ 395,98" e o Pix com "Valor: 395.98" (ponto, como o banco escreve). */
    private val receipt =
        "NOTA FISCAL Império Distribuidora Saco de lixo R$ 15,90 5 R$ 79,50 Bom ar R$ 16,00 1 R$ 16,00 " +
                "Total R$ 395,98 15/06/2026"

    private fun pix(value: String) =
        "Comprovante de transação Pix Transferência Valor: $value Descrição: Pix para MKL SERVICOS Multa: R$ 0,00"

    @Test
    fun `valor do Pix igual ao total do cupom passa`() {
        assertNull(check(receipt, pix("395.98")).amountMismatch)
        assertTrue(check(receipt, pix("395.98")).isValid)
    }

    @Test
    fun `valor do Pix diferente do cupom vira pendencia com os dois valores`() {
        val result = check(receipt, pix("405.98"))

        assertEquals(AmountMismatch(BigDecimal("405.98"), BigDecimal("395.98")), result.amountMismatch)
        assertEquals(
            "Valor do comprovante (R$ 405,98) diferente do cupom/nota (R$ 395,98): confira e troque o documento errado",
            ExpenseDocumentRules.issueNote(result),
        )
    }

    @Test
    fun `OCR que erra o total mas acerta o valor em outra linha nao gera pendencia falsa`() {
        // Cupom real: o OCR leu o total como 425,00, mas "Valor bruto R$ 125,00" está certo.
        assertNull(check(cupomOcr, pixToStore).amountMismatch)
    }

    @Test
    fun `valores em milhar e boleto pago com Valor do pagamento`() {
        val bill = "DANFE NOTA FISCAL VALOR TOTAL DA NOTA 1.234,56"
        val boleto = "Comprovante de pagamento Pix Valor do pagamento: R$ 1.234,56 Desconto: R$ 0,00"

        assertNull(check(bill, boleto).amountMismatch)
        assertEquals(
            AmountMismatch(BigDecimal("1243.56"), BigDecimal("1234.56")),
            check(bill, boleto.replace("1.234,56", "1.243,56")).amountMismatch,
        )
    }

    @Test
    fun `dois comprovantes que somam o total do cupom passam`() {
        assertNull(check(receipt, pix("200.00"), pix("195.98")).amountMismatch)
    }

    @Test
    fun `sem valor legivel de um dos lados nada e afirmado`() {
        // Pix lido pelo OCR com o valor embaralhado ("48055") e cupom sem nenhum valor.
        assertNull(check(receipt, "Comprovante de transacao Pix Valor: 48055").amountMismatch)
        assertNull(check("CUPOM FISCAL foto ilegível", pix("10.00")).amountMismatch)
    }

    @Test
    fun `cupom sem linha de total ainda aponta a diferenca`() {
        val result = check("CUPOM FISCAL Item 1 R$ 20,00 Item 2 R$ 30,00", pix("60.00"))

        assertEquals(AmountMismatch(BigDecimal("60.00"), null), result.amountMismatch)
        assertEquals(
            "Valor do comprovante (R$ 60,00) não aparece no cupom/nota: confira e troque o documento errado",
            ExpenseDocumentRules.issueNote(result),
        )
    }

    @Test
    fun `dois cupons fotografados que somam o Pix passam, mesmo com a virgula perdida pelo OCR`() {
        // "3.2 COMPRA DE VASSOURAS": cupons de R$ 59,97 e R$ 44,97 ("R$ 44 97" no OCR), um Pix de 104,94.
        val first = "Documento Auxiliar da Nota Fiscal de Consumidor VALOR A PAGAR RS 59,97 Outros R} 99,9"
        val second = "Nata Faseal de Consumidar Qtde total de rtens 3 Valor total R$ 44 97 PI> 44 97"
        val content =
            DocumentContent(listOf(first, second, pix("104.94")), listOf(elvesCertificate), ocrPages = setOf(0, 1))

        assertNull(ExpenseDocumentRules.check(content).amountMismatch)
    }

    @Test
    fun `desconto no cupom - vale o valor a pagar`() {
        // "3.12 Compras de Mop": Valor Total 175,70, desconto 8,78, a pagar "R$ 166 92" (vírgula perdida).
        val cupom =
            "DOCUMENTO AUXILIAR DA NOTA FISCAL Valor Total RS 175,70 Acrescimo / Desconto R$ 8.78 Valor a pagar R$ 166 92"

        assertNull(check(cupom, pix("166.92")).amountMismatch)
        assertEquals(BigDecimal("166.92"), check(cupom, pix("180.00")).amountMismatch?.fiscal)
    }

    @Test
    fun `um digito diferente em foto e erro do OCR, mas em texto e pendencia`() {
        // Conta de luz fotografada: o OCR leu 781,57 como 761,57.
        val bill = "Documento Auxiliar da Nota Fiscal de Energia TOTAL A PAGAR R$ 761,57"
        val photo = DocumentContent(listOf(bill, pix("781.57")), listOf(elvesCertificate), ocrPages = setOf(0))

        assertNull(ExpenseDocumentRules.check(photo).amountMismatch)
        assertEquals(
            AmountMismatch(BigDecimal("781.57"), BigDecimal("761.57")),
            ExpenseDocumentRules.check(photo.copy(ocrPages = emptySet())).amountMismatch,
        )
    }

    @Test
    fun `foto sem linha de total legivel nao gera pendencia de valor`() {
        val photo = DocumentContent(
            listOf("FATURA DE SANEAMENTO VALOR FATBRADO (RS): alone a7 25", pix("64.16")),
            listOf(elvesCertificate),
            ocrPages = setOf(0),
        )

        assertNull(ExpenseDocumentRules.check(photo).amountMismatch)
    }

    @Test
    fun `pendencia junta o que falta e a diferenca de valor e continua automatica`() {
        val note = ExpenseDocumentRules.issueNote(check(receipt, pix("405.98"), signers = emptyList()))!!

        assertEquals(
            "Falta assinatura de Elves Lincoln; Valor do comprovante (R$ 405,98) diferente do cupom/nota " +
                    "(R$ 395,98): confira e troque o documento errado",
            note,
        )
        assertTrue(ExpenseDocumentRules.isAutomaticNote(note))
        assertFalse(ExpenseDocumentRules.isAutomaticNote("$note; e o recibo está rasgado"))
    }
}
