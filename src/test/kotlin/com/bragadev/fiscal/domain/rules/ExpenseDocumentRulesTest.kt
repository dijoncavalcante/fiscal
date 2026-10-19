package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseCheck
import com.bragadev.fiscal.domain.model.ExpenseRequirement.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Textos tirados das despesas reais da Conta da Manutenção (PDF e OCR de fotos). */
class ExpenseDocumentRulesTest {
    private val cupomOcr = "COMPROVANTE DE VENDA 46.182.254 ANDRESSA DOS SANTOS FERREIRA CNPU: AG 182.254/0001-79 " +
            "VENDA 36791 | 76 DRENO 5x R$ 25,00 LIQUIDO TOTAL OO. R$ 425,00"
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
        assertTrue(check(cupomOcr, "COMPROVANTE DE TRANSFERÊNCIA TED Valor R$ 300,00").isValid)
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
}
