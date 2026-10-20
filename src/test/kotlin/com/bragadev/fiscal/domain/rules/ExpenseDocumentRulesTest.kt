package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.model.AmountProblemKind.*
import com.bragadev.fiscal.domain.model.ExpenseRequirement.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Textos tirados das despesas reais da Conta da Manutenção (PDF e OCR de fotos). */
class ExpenseDocumentRulesTest {
    private val cupom = "COMPROVANTE DE VENDA 46.182.254 ANDRESSA DOS SANTOS FERREIRA VENDA 36791 | 76 DRENO " +
            "5x R$ 25,00 Valor bruto R$ 125,00 Desconto na venda R$ 0,00 LIQUIDO TOTAL R$ 125,00"
    private val pixToStore = "Comprovante de transação Pix Pagamento Data da operação: 19/09/2026 10:55:34 " +
            "Dados do recebedor Nome: 46.182.254 ANDRESSA DOS SANTOS FERREIRA Valor: 125.00 " +
            "Id da transação: E60746948202609191355M3943eq0hGo"
    private val pixToElves = "Comprovante de transação Pix Transferência Data da operação: 05/08/2026 - 15:38 " +
            "Dados do recebedor Nome: Elves Lincoln Menezes de Lima CPF/CNPJ: ***.451.352-** " +
            "Descrição: Pix para Elves Lima - lâmpadas"
    private val elvesCertificate = "CN=ELVES LINCOLN MENEZES DE LIMA"

    private fun check(vararg pages: String, signers: List<String> = listOf(elvesCertificate)) =
        ExpenseDocumentRules.check(DocumentContent(pages.toList(), signers))

    private fun photo(vararg pages: String, ocrPages: Set<Int>) =
        ExpenseDocumentRules.check(DocumentContent(pages.toList(), listOf(elvesCertificate), ocrPages))

    @Test
    fun `cupom, comprovante Pix com o mesmo valor e assinatura no certificado = despesa verificada`() {
        val result = check(cupom, pixToStore)

        assertTrue(result.isValid, "faltou: ${result.missing}, valor: ${result.amountProblem}")
    }

    @Test
    fun `nome do Elves so como recebedor do Pix nao conta como assinatura`() {
        val result = check(cupom, pixToElves, signers = emptyList())

        assertEquals(setOf(SIGNATURE), result.missing)
    }

    @Test
    fun `selo do gov br no texto vale como assinatura, qualquer que seja a data`() {
        val stamp = "Documento assinado digitalmente ELVES LINCOLN MENEZES DE LIMA Data: 21/08/2026 16:16:37-0300 " +
                "Verifique em https://validar. iti. gov.br"
        val otherDate = stamp.replace("21/08/2026 16:16:37", "03/01/2027 09:00:00")

        assertTrue(check("$cupom $stamp", pixToStore, signers = emptyList()).isValid)
        assertTrue(check(cupom, "$pixToStore $otherDate", signers = emptyList()).isValid)
    }

    @Test
    fun `assinatura de outra pessoa nao vale`() {
        val stamp =
            "Documento assinado digitalmente FULANO DE TAL Data: 21/08/2026 Verifique em https://validar.iti.gov.br"

        assertEquals(setOf(SIGNATURE), check("$cupom $stamp", pixToStore, signers = listOf("CN=FULANO DE TAL")).missing)
    }

    @Test
    fun `orcamento nao e cupom nem nota fiscal`() {
        val budget = "MKL Comércio e Serviços Orçamento 007-2026 Cliente: Andrei Alves Materiais Descrição Unidade " +
                "Preço unitário Qtd. Caixa de copo 180ml R$ 108,00"

        assertEquals(setOf(FISCAL_DOCUMENT), check(budget, pixToStore).missing)
        // Orçamento que se diz "recibo" também não vale.
        assertEquals(setOf(FISCAL_DOCUMENT), check("ORÇAMENTO / RECIBO Total R$ 125,00", pixToStore).missing)
    }

    @Test
    fun `recibo vale como comprovante fiscal`() {
        val receipt = "RECIBO Recebi de Congregação a importância de R$ 125,00 referente a conserto do portão. " +
                "Total R$ 125,00 Manaus, 19/09/2026"

        assertTrue(check(receipt, pixToStore).isValid)
        // Foto de recibo com uma letra trocada pelo OCR.
        assertEquals(emptySet(), photo("RECJBO Valor total R$ 125,00", pixToStore, ocrPages = setOf(0)).missing)
    }

    @Test
    fun `comprovante do banco chamado de recibo nao conta como cupom`() {
        val bankReceipt = "Recibo de transferência Pix Comprovante Valor: 125.00 Id da transação: " +
                "E60746948202609191355M3943eq0hGo"

        assertEquals(setOf(FISCAL_DOCUMENT), check(bankReceipt).missing)
    }

    @Test
    fun `nota fiscal lida com erros de OCR ainda e reconhecida`() {
        // OCR de foto de conta de luz: "Documento Auxiliar da Nota Fiscal" e "Chave de Acesso" com letras trocadas.
        val bill = "Amazonas Energia [a] Documents Aus a Nata Fecal Frergia Coneulte pein Chave de Aceesn"

        assertEquals(emptySet(), check(bill, pixToStore).missing)
        assertEquals(emptySet(), check("DANFE Documento Auxiliar da Nota Fiscal Eletronica NF-e", pixToStore).missing)
        assertEquals(emptySet(), check("CUPOM FISCAL ELETRONICO - SAT", pixToStore).missing)
    }

    @Test
    fun `fatura que so oferece pagar com Pix nao e comprovante`() {
        val bill = "NOTA FISCAL FATURA DE SERVIÇOS Pague com Pix: aponte a câmera para o QR Code. Vencimento 10/09/2026"

        assertEquals(setOf(PAYMENT_PROOF), check(bill).missing)
    }

    @Test
    fun `comprovante de TED e identificador do Pix contam como pagamento`() {
        assertTrue(check(cupom, "COMPROVANTE DE TRANSFERÊNCIA TED Valor R$ 125,00").isValid)
        assertEquals(emptySet(), check(cupom, "Pagamento efetuado E60746948202607151342M3943amS3S8").missing)
    }

    @Test
    fun `pagina em branco nao tem nada`() {
        val result = check("", signers = emptyList())

        assertEquals(setOf(FISCAL_DOCUMENT, PAYMENT_PROOF, SIGNATURE), result.missing)
        // Sem comprovante e sem cupom não há valor a comparar: a pendência "Falta …" já diz tudo.
        assertNull(result.amountProblem)
    }

    @Test
    fun `pendencia diz em poucas palavras o que falta`() {
        assertNull(ExpenseDocumentRules.issueNote(ExpenseCheck()))
        assertEquals(
            "Falta assinatura de Elves Lincoln",
            ExpenseDocumentRules.issueNote(ExpenseCheck(setOf(SIGNATURE)))
        )
        assertEquals(
            "Falta cupom, nota fiscal ou recibo e assinatura de Elves Lincoln",
            ExpenseDocumentRules.issueNote(ExpenseCheck(setOf(SIGNATURE, FISCAL_DOCUMENT))),
        )
        assertEquals(
            "Falta cupom, nota fiscal ou recibo, comprovante Pix/TED e assinatura de Elves Lincoln",
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

    @Test
    fun `pendencias gravadas pela versao anterior continuam automaticas e sao atualizadas`() {
        assertTrue(ExpenseDocumentRules.isAutomaticNote("Falta cupom ou nota fiscal e assinatura de Elves Lincoln"))
        assertTrue(
            ExpenseDocumentRules.isAutomaticNote(
                "Valor do comprovante (R$ 60,00) não aparece no cupom/nota: confira e troque o documento errado",
            ),
        )
    }

    // ---- Valor do comprovante × total do cupom/nota ----

    /** Nota real: "Total R$ 395,98" e o Pix com "Valor: 395.98" (ponto, como o banco escreve). */
    private val receipt =
        "NOTA FISCAL Império Distribuidora Saco de lixo R$ 15,90 5 R$ 79,50 Bom ar R$ 16,00 1 R$ 16,00 " +
                "Total R$ 395,98 15/06/2026"

    private fun pix(value: String) =
        "Comprovante de transação Pix Transferência Valor: $value Descrição: Pix para MKL SERVICOS Multa: R$ 0,00"

    @Test
    fun `valor do Pix igual ao total do cupom passa`() {
        assertNull(check(receipt, pix("395.98")).amountProblem)
        assertTrue(check(receipt, pix("395.98")).isValid)
        assertEquals("R$ 395,98 em \"TOTAL\", página 1 (texto do PDF)", check(receipt, pix("395.98")).amountEvidence)
    }

    @Test
    fun `valor do Pix diferente do cupom vira pendencia com os dois valores`() {
        val result = check(receipt, pix("405.98"))

        assertEquals(AmountProblem(MISMATCH, BigDecimal("405.98"), BigDecimal("395.98")), result.amountProblem)
        assertEquals(
            "Valor do comprovante (R$ 405,98) diferente do cupom/nota (R$ 395,98): confira e troque o documento errado",
            ExpenseDocumentRules.issueNote(result),
        )
    }

    @Test
    fun `Pix igual ao preco de um item, e nao ao total, e diferenca`() {
        // R$ 79,50 aparece no cupom, mas como valor de item: o total é 395,98.
        assertEquals(
            AmountProblem(MISMATCH, BigDecimal("79.50"), BigDecimal("395.98")),
            check(receipt, pix("79.50")).amountProblem,
        )
    }

    @Test
    fun `valor bruto nao e o total`() {
        // Cupom real fotografado: o OCR leu o total como 425,00; "Valor bruto R$ 125,00" não conta como total.
        val cupomOcr = "COMPROVANTE DE VENDA 46.182.254 VENDA 36791 | 76 DRENO 5x R$ 25,00 RS 425,00 " +
                "Valor bruto R$ 125,00 Desconto na venda R$ 0,00 LIQUIDO TOTAL OO. R$ 425,00"

        val result = photo(cupomOcr, pixToStore, ocrPages = setOf(0))

        assertEquals(AmountProblem(UNCERTAIN_READING, BigDecimal("125.00"), BigDecimal("425.00")), result.amountProblem)
        assertEquals(
            "Valor do comprovante (R$ 125,00) diferente do lido na foto do cupom/nota (R$ 425,00), que pode ter " +
                    "sido mal lida: confira à mão",
            ExpenseDocumentRules.issueNote(result),
        )
    }

    @Test
    fun `valor do comprovante com separador de milhar em ingles`() {
        // Comprovante real do banco next: "Valor: 1,051.49".
        val bill = "DANFE NOTA FISCAL DE ENERGIA TOTAL A PAGAR R$ 1.051,49"
        val next = "Comprovante de transação Pix Pagamento Dados do pagamento Valor: 1,051.49 Descrição: -"

        assertNull(check(bill, next).amountProblem)
        assertEquals(
            AmountProblem(MISMATCH, BigDecimal("1015.49"), BigDecimal("1051.49")),
            check(bill, next.replace("1,051.49", "1,015.49")).amountProblem,
        )
    }

    @Test
    fun `valor a pagar com letras trocadas pelo OCR e valor do pedido contam como total`() {
        assertNull(photo("NOTA FISCAL VALOR & PAGAR RS 59,97", pix("59.97"), ocrPages = setOf(0)).amountProblem)
        assertNull(photo("NOTA FISCAL Valor a Pag TL 111,09", pix("111.09"), ocrPages = setOf(0)).amountProblem)
        // DANFE fotografada: o total da nota fica numa tabela longe do título, mas o pedido traz o valor.
        val danfe =
            "DANFE VALOR TOTAL DOS PRODUTOS 213,09 REF PEDIDO VENDA NRO -152985 VALOR PEDIDO: 232,00 FORMA PAG. PIX"
        assertNull(photo(danfe, pix("232.00"), ocrPages = setOf(0)).amountProblem)
    }

    @Test
    fun `valores em milhar e boleto pago com Valor do pagamento`() {
        val bill = "DANFE NOTA FISCAL VALOR TOTAL DA NOTA 1.234,56"
        val boleto = "Comprovante de pagamento Pix Valor do pagamento: R$ 1.234,56 Desconto: R$ 0,00"

        assertNull(check(bill, boleto).amountProblem)
        assertEquals(
            AmountProblem(MISMATCH, BigDecimal("1243.56"), BigDecimal("1234.56")),
            check(bill, boleto.replace("1.234,56", "1.243,56")).amountProblem,
        )
    }

    @Test
    fun `dois comprovantes que somam o total do cupom passam`() {
        assertNull(check(receipt, pix("200.00"), pix("195.98")).amountProblem)
    }

    @Test
    fun `valor do comprovante nao lido vira pendencia para conferir a mao`() {
        // Pix lido pelo OCR com o valor embaralhado ("48055").
        val result = check(receipt, "Comprovante de transacao Pix Valor: 48055")

        assertEquals(AmountProblem(PAYMENT_UNREADABLE), result.amountProblem)
        assertEquals(
            "Valor do comprovante Pix/TED não foi lido: confira à mão se é igual ao total do cupom/nota",
            ExpenseDocumentRules.issueNote(result),
        )
    }

    @Test
    fun `cupom sem linha de total vira pendencia para conferir a mao`() {
        val result = check("CUPOM FISCAL Item 1 R$ 20,00 Item 2 R$ 30,00", pix("60.00"))

        assertEquals(AmountProblem(TOTAL_UNREADABLE, BigDecimal("60.00")), result.amountProblem)
        assertEquals(
            "Total do cupom/nota não foi lido: confira à mão se é igual ao comprovante (R$ 60,00)",
            ExpenseDocumentRules.issueNote(result),
        )
        // Foto sem o total legível: mesma coisa.
        val bill = photo("FATURA DE SANEAMENTO VALOR FATBRADO (RS): alone a7 25", pix("64.16"), ocrPages = setOf(0))
        assertEquals(TOTAL_UNREADABLE, bill.amountProblem?.kind)
    }

    @Test
    fun `cupom e comprovante na mesma pagina - o Valor do Pix nao vale como total`() {
        val page = "CUPOM FISCAL Item R$ 50,00 Comprovante de transação Pix Valor: 50.00"

        assertEquals(TOTAL_UNREADABLE, check(page).amountProblem?.kind)
        assertNull(check("CUPOM FISCAL TOTAL R$ 50,00 Comprovante de transação Pix Valor: 50.00").amountProblem)
    }

    @Test
    fun `dois cupons fotografados que somam o Pix passam, mesmo com a virgula perdida pelo OCR`() {
        // "3.2 COMPRA DE VASSOURAS": cupons de R$ 59,97 e R$ 44,97 ("R$ 44 97" no OCR), um Pix de 104,94.
        val first = "Documento Auxiliar da Nota Fiscal de Consumidor VALOR A PAGAR RS 59,97 Outros R} 99,9"
        val second = "Nata Faseal de Consumidar Qtde total de rtens 3 Valor total R$ 44 97 PI> 44 97"

        assertNull(photo(first, second, pix("104.94"), ocrPages = setOf(0, 1)).amountProblem)
    }

    @Test
    fun `desconto no cupom - vale o valor a pagar`() {
        // "3.12 Compras de Mop": Valor Total 175,70, desconto 8,78, a pagar "R$ 166 92" (vírgula perdida).
        val cupom =
            "DOCUMENTO AUXILIAR DA NOTA FISCAL Valor Total RS 175,70 Acrescimo / Desconto R$ 8.78 Valor a pagar R$ 166 92"

        assertNull(check(cupom, pix("166.92")).amountProblem)
        assertEquals(BigDecimal("166.92"), check(cupom, pix("180.00")).amountProblem?.fiscal)
    }

    @Test
    fun `valores que nao batem em foto pedem conferencia, em texto sao diferenca`() {
        // Conta de luz fotografada: o OCR leu 781,57 como 761,57.
        val bill = "Documento Auxiliar da Nota Fiscal de Energia TOTAL A PAGAR R$ 761,57"

        assertEquals(
            AmountProblem(UNCERTAIN_READING, BigDecimal("781.57"), BigDecimal("761.57")),
            photo(bill, pix("781.57"), ocrPages = setOf(0)).amountProblem,
        )
        assertEquals(
            AmountProblem(MISMATCH, BigDecimal("781.57"), BigDecimal("761.57")),
            check(bill, pix("781.57")).amountProblem,
        )
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

    // ---- Total pela posição na página (tabela da NFC-e) ----

    /** Texto real do OCR da NFC-e fotografada "3.5 Despesa - Compra de refil para bebedouro" (trechos). */
    private val refillOcr = "Documento Auxiliar da Nota Fiscal de Consumidor Elatrénica Bae\n" +
            "copeo TUN _UNITARIO. ‘TOTAL\nFILTRO REFIL. CARTUGHO FILTRANTE HF 200 COMPATIVEL AQUALAF\n" +
            "SEMGTIN 1 UND 50,00 60,00\ntens SubTotal (R$) Desconta (R$)  Frete (Rd) TOTAL (RR |\n" +
            "1 50,00 0,00 0,00 50,00)\nime BOLETO BANCARIO 60,00\n"
    private val refillPix = "Comprovante de transação Pix Transferência Dados do pagamento Valor: 50.00 " +
            "Descrição: Reembolso manutenção bebedouro Id da transação: E60746948202610191833M3943yxFToY"

    private fun w(text: String, x: Int, y: Int, width: Int = 15 * text.length, confidence: Float = 95f) =
        PageWord(text, x.toFloat(), y.toFloat(), width.toFloat(), 28f, confidence)

    /** Palavras da mesma NFC-e com a posição (OCR 200 DPI). */
    private fun refillWords(total: String = "50,00", confidence: Float = 94f, subtotal: String = "50,00") = listOf(
        w("Itens", 185, 1047), w("Sub-Total", 330, 1047, 120), w("(R$)", 455, 1047, 50), w("Desconto", 600, 1047, 100),
        w("(R$)", 705, 1047, 50), w("Frete", 820, 1047, 60), w("(R$)", 885, 1047, 50), w("TOTAL", 1169, 1047, 96),
        w("(R$)", 1270, 1047, 50),
        w("1", 190, 1085, 15), w(subtotal, 390, 1085, 90), w("0,00", 640, 1085, 70), w("0,00", 850, 1085, 70),
        w(total, 1230, 1085, 100, confidence),
    )

    private fun refill(pix: String = refillPix, words: List<PageWord> = refillWords()) =
        ExpenseDocumentRules.check(
            DocumentContent(
                listOf(refillOcr, pix), listOf(elvesCertificate), ocrPages = setOf(0),
                layouts = listOf(PageLayout(0, words, fromOcr = true)),
            ),
        )

    @Test
    fun `NFC-e fotografada - total embaixo de TOTAL (R$) bate com o Pix e diz de onde veio`() {
        // Sem as posições, o texto não liga "TOTAL (RR |" ao "50,00" da linha de baixo.
        assertEquals(TOTAL_UNREADABLE, photo(refillOcr, refillPix, ocrPages = setOf(0)).amountProblem?.kind)

        val result = refill()

        assertTrue(result.isValid, "valor: ${result.amountProblem}")
        assertEquals(
            "R$ 50,00 abaixo de \"TOTAL (R$)\", página 1 (foto, conta da tabela confere)",
            result.amountEvidence,
        )
    }

    @Test
    fun `NFC-e fotografada com Pix de outro valor continua pendencia`() {
        val result = refill(pix = refillPix.replace("50.00", "60.00"))

        assertEquals(AmountProblem(UNCERTAIN_READING, BigDecimal("60.00"), BigDecimal("50.00")), result.amountProblem)
        assertNull(result.amountEvidence)
    }

    @Test
    fun `total lido com confianca baixa nao vale nem para bater nem para afirmar diferenca`() {
        // A conta da tabela não fecha (SubTotal ilegível) e o total saiu com 30% de confiança.
        val words = refillWords(confidence = 30f, subtotal = "5O,0O")

        assertEquals(TOTAL_UNREADABLE, refill(words = words).amountProblem?.kind)
        assertEquals(
            TOTAL_UNREADABLE,
            refill(pix = refillPix.replace("50.00", "60.00"), words = words).amountProblem?.kind
        )
    }

    @Test
    fun `confianca media basta para bater, mas nao para afirmar diferenca`() {
        val words = refillWords(confidence = 55f, subtotal = "5O,0O")

        assertTrue(refill(words = words).isValid)
        assertEquals(
            TOTAL_UNREADABLE,
            refill(pix = refillPix.replace("50.00", "60.00"), words = words).amountProblem?.kind
        )
    }

    @Test
    fun `coluna TOTAL da lista de itens igual ao Pix nao e aceita como total`() {
        val itemsOnly = listOf(
            w("CÓDIGO", 185, 838), w("UNITÁRIO", 880, 838), w("TOTAL", 1225, 838, 103),
            w("FILTRO", 185, 900), w("50,00", 1000, 900, 90), w("50,00", 1230, 900, 90),
        )

        assertEquals(TOTAL_UNREADABLE, refill(words = itemsOnly).amountProblem?.kind)
    }

    @Test
    fun `valor do Pix embaralhado no primeiro OCR e lido numa nova leitura`() {
        val pixPhoto = "Comprovante de transacao Pix Valor: 560000 Id da transacao: E60746948202610191833M3943yxFToY"
        val pixWords = listOf(w("Valor:", 100, 400), w("56.00", 220, 400))
        val content = DocumentContent(
            listOf("CUPOM FISCAL TOTAL R$ 56,00", pixPhoto), listOf(elvesCertificate), ocrPages = setOf(0, 1),
            layouts = listOf(PageLayout(1, pixWords, fromOcr = true)),
        )

        assertEquals(
            PAYMENT_UNREADABLE,
            ExpenseDocumentRules.check(content.copy(layouts = emptyList())).amountProblem?.kind
        )
        assertTrue(ExpenseDocumentRules.check(content).isValid)
    }

    @Test
    fun `leitura so termina quando o valor esta resolvido`() {
        // Cupom e comprovante achados, mas o total não: o leitor precisa continuar.
        assertFalse(
            ExpenseDocumentRules.hasEnoughText(
                DocumentContent(
                    listOf(refillOcr, refillPix),
                    ocrPages = setOf(0)
                )
            )
        )
        assertTrue(
            ExpenseDocumentRules.hasEnoughText(
                DocumentContent(
                    listOf(refillOcr, refillPix), ocrPages = setOf(0),
                    layouts = listOf(PageLayout(0, refillWords(), fromOcr = true)),
                ),
            ),
        )
        // Diferença num PDF de texto é resposta definitiva: não adianta ler de novo.
        assertTrue(ExpenseDocumentRules.hasEnoughText(DocumentContent(listOf(receipt, pix("405.98")))))
    }

    @Test
    fun `pendencias de valor nao conferido sao automaticas`() {
        val notes = listOf(
            check(receipt, "Comprovante de transacao Pix Valor: 48055"),
            check("CUPOM FISCAL Item 1 R$ 20,00", pix("60.00")),
            photo("NOTA FISCAL TOTAL A PAGAR R$ 761,57", pix("781.57"), ocrPages = setOf(0)),
        ).map { ExpenseDocumentRules.issueNote(it)!! }

        notes.forEach { assertTrue(ExpenseDocumentRules.isAutomaticNote(it), it) }
    }
}
