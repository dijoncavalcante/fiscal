package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Extrato bancário (8. Extrato Bancário da Congregação, 5. Extrato Bancário da Manutenção), com os textos como o
 * PDFBox e o OCR leem os extratos reais do pendrive de 2025-2026.
 */
class BankStatementRulesTest {
    private val august = YearMonth.of(2026, 8)
    private val next = BankStatementRules.accountFor(AccountType.MANUTENCAO)!!
    private val nubank = BankStatementRules.accountFor(AccountType.CONGREGACAO)!!

    /** "5. Extrato Bancário" de agosto/2026 (o next embaralha a ordem das colunas do cabeçalho). */
    private val nextAugust = DocumentContent(
        listOf(
            """
            Extrato de Conta Poupança
            Data: 31/08/2026 - 15h06
            Página  de 1 2
            Banco de origem: next (237 - Banco Bradesco S.A.)
            Extrato de: Agência:  | Conta:  | Tipo: 3943 286256-5 Conta Poupança
            Nome: DANILO CAVALCANTE BRAGA
            Movimentação entre: 01/08/2026 e 31/08/2026
            Data Histórico Docto. Crédito (R$) Débito (R$) Saldo (R$)
            21/07/2026 Saldo Anterior R$ 8.946,52
            05/08/2026
             3205-PIX ENVIADO
            DES: Elves Lincoln Menezes 05/08
            1538161 R$ -63,00 R$ 8.883,52
            """.trimIndent(),
            "Central de Relacionamento next 0800 275 6398",
        ),
        signers = listOf("FILIPE DA SILVA COSTA"),
    )

    /** "8. Extrato Bancário" de agosto/2026 (Nubank): o CNPJ só aparece na página 4. */
    private val nubankAugust = DocumentContent(
        listOf(
            """
            Filipe da Silva Costa
             •••.369.732-••  0001 CPF Agência Conta
            15077760-0
             a 02 DE AGOSTO DE 2026 31 DE AGOSTO DE 2026 VALORES EM R$
             Saldo final do período
            Transferência enviada pelo Pix Danilo Cavalcante Braga - •••.638.182-•• - BCO
            BRADESCO S.A. (0237) Agência: 3943 Conta:
            286256-5
            800,00
            Extrato gerado dia 01 de setembro de 2026 às 09:41 1 de 5
            """.trimIndent(),
            "Extrato gerado dia 01 de setembro de 2026 às 09:41 2 de 5",
            "Extrato gerado dia 01 de setembro de 2026 às 09:41 3 de 5",
            """
            Nu Financeira S.A. - Sociedade de Credito, Financiamento e
            Investimento
            CNPJ: 30.680.829/0001-43
            Nu Pagamentos S.A. - Instituição de
            Pagamento
            CNPJ: 18.236.120/0001-58
            """.trimIndent(),
        ),
        signers = listOf("FILIPE DA SILVA COSTA"),
    )

    private fun requirements(account: BankAccountProfile, month: YearMonth? = august, signer: String? = FILIPE) =
        StatementRequirements(signer, month, account)

    @Test
    fun `extrato do next de agosto assinado pelo Filipe esta certo`() {
        val result = BankStatementRules.check(nextAugust, requirements(next))
        assertTrue(result.check.isValid)
        assertNull(result.note)
        assertEquals(
            "next (237 - Banco Bradesco S.A.), Agência 3943, Conta 286256-5, DANILO CAVALCANTE BRAGA; " +
                    "de 01/08/2026 a 31/08/2026; assinado por Filipe da Silva Costa",
            result.check.amountEvidence,
        )
    }

    @Test
    fun `extrato do Nubank de agosto assinado pelo Filipe esta certo`() {
        val result = BankStatementRules.check(nubankAugust, requirements(nubank))
        assertTrue(result.check.isValid, result.note)
        assertTrue(result.check.amountEvidence!!.contains("de 02/08/2026 a 31/08/2026"))
    }

    @Test
    fun `extrato de outro mes pede a troca`() {
        val result = BankStatementRules.check(nextAugust, requirements(next, month = YearMonth.of(2026, 9)))
        assertEquals(setOf(ExpenseRequirement.STATEMENT_MONTH), result.check.missing)
        assertEquals(
            "Extrato de 01/08/2026 a 31/08/2026, mas o mês em edição é setembro de 2026: troque pelo extrato do mês",
            result.note,
        )
        assertTrue(BankStatementRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `extrato da outra conta nao vale, mesmo citando a conta certa numa transferencia`() {
        // O Nubank da Congregação tem um Pix para a conta do next (Agência 3943, Conta 286256-5, Danilo).
        val result = BankStatementRules.check(nubankAugust, requirements(next))
        assertTrue(ExpenseRequirement.BANK_ACCOUNT in result.check.missing)
        assertTrue(result.note!!.startsWith("Extrato de outra conta (não tem banco next (237 - Banco Bradesco S.A.)"))
        assertTrue(result.note.contains("o extrato precisa ser da conta ${next.description}"))
        assertTrue(BankStatementRules.isAutomaticNote(result.note))

        val other = BankStatementRules.check(nextAugust, requirements(nubank))
        assertTrue(ExpenseRequirement.BANK_ACCOUNT in other.check.missing)
        assertTrue(other.note!!.contains("Conta 15077760-0"))
    }

    @Test
    fun `assinatura de outra pessoa ou sem responsavel configurado vira pendencia`() {
        val elves = BankStatementRules.check(
            nextAugust.copy(signers = listOf("ELVES LINCOLN MENEZES DE LIMA")), requirements(next),
        )
        assertEquals(setOf(ExpenseRequirement.SIGNATURE), elves.check.missing)
        assertEquals("Falta assinatura de $FILIPE", elves.note)

        val notConfigured = BankStatementRules.check(nextAugust, requirements(next, signer = null))
        assertTrue(notConfigured.note!!.startsWith("Falta assinatura do responsável pelos extratos (configure"))
        assertTrue(BankStatementRules.isAutomaticNote(notConfigured.note))
    }

    @Test
    fun `selo gov br completo no texto vale como assinatura, a data e ignorada`() {
        val stamp = "Documento assinado digitalmente FILIPE DA SILVA COSTA Data: 02/10/2026 00:25:40-0300 " +
                "Verifique em https://validar.iti.gov.br"
        val content = nextAugust.copy(signers = emptyList(), pageTexts = nextAugust.pageTexts + stamp)
        assertTrue(BankStatementRules.check(content, requirements(next)).check.isValid)
    }

    @Test
    fun `extrato digitalizado - OCR com letras trocadas ainda e reconhecido`() {
        // "8. Extrato Bancário" de março/2026 (CamScanner): "O1 DE MARGO", conta do Nubank.
        val scanned = DocumentContent(
            listOf(
                """
                4 Filipe da Silva Costa
                . ou CPF ---.369.732--- Agéncia 0001 Conta
                15077760-0
                O1 DE MARGO DE 2026 a 01 DE ABRIL DE 2026 VALORES EM R$
                Transferéncia Recebida Mistefany Cavalcante Braga - --+.059.G02-+ - NU 30,00
                PAGAMENTQOS - IP (6260) Agéncia: 1 Conta:
                """.trimIndent(),
                "Nu Pagamentos S.A. - Instituição de Pagamento CNPJ: 18.236.120/0001-58",
            ),
            signers = listOf("FILIPE DA SILVA COSTA"),
            ocrPages = setOf(0, 1),
        )
        val result = BankStatementRules.check(scanned, requirements(nubank, month = YearMonth.of(2026, 3)))
        assertTrue(result.check.isValid, result.note)

        // Um dígito da conta trocado pelo OCR ("45077760-0") ainda vale numa foto.
        val misread = scanned.copy(
            pageTexts = listOf(
                scanned.pageTexts[0].replace(
                    "15077760",
                    "45077760"
                )
            ) + scanned.pageTexts[1]
        )
        assertTrue(BankStatementRules.check(misread, requirements(nubank, month = YearMonth.of(2026, 3))).check.isValid)
    }

    @Test
    fun `digitalizado - o que nao foi lido pede para conferir a mao, nunca para trocar`() {
        val scanned = nextAugust.copy(
            pageTexts = listOf(
                nextAugust.pageTexts[0].replace("Movimentação entre: 01/08/2026 e 31/08/2026", "Movimentacao")
                    .replace("DANILO", "D4N1L0 X")
            ),
            ocrPages = setOf(0),
        )
        val note = BankStatementRules.check(scanned, requirements(next)).note!!
        assertEquals(
            "Conta do extrato não confirmada na foto (não li titular DANILO CAVALCANTE BRAGA): confira à mão se é a " +
                    "conta ${next.description}; Período do extrato não foi lido: confira à mão se é de agosto de 2026",
            note,
        )
        assertTrue(BankStatementRules.isAutomaticNote(note))
    }

    @Test
    fun `periodo - meses parecidos nao se confundem e vale o mes com mais dias`() {
        fun period(text: String) = BankStatementRules.findPeriod(listOf(text))?.first

        // "JULHO" não pode virar "JUNHO", nem "DEZEMBRO" virar "SETEMBRO".
        assertEquals(
            StatementPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)),
            period(" a 01 DE JULHO DE 2026 01 DE AGOSTO DE 2026 VALORES EM R$"),
        )
        val december = period("01 DE DEZEMBRO DE 2025 a 08 DE JANEIRO DE 2026 VALORES EM R$")!!
        assertEquals(YearMonth.of(2025, 12), december.mainMonth)
        // OCR: "Movimentacao entre: —_ 01/05/2026 ¢ 31/05/2026".
        assertEquals(YearMonth.of(2026, 5), period("Movimentacao entre: —_ 01/05/2026 ¢ 31/05/2026")!!.mainMonth)
        assertEquals(YearMonth.of(2026, 1), period("O9 DE JANEIRO DE 2026 a 31 DE JANEIRO DE 2026")!!.mainMonth)
        // A data em que o extrato foi gerado não é período.
        assertNull(period("Extrato gerado dia 01 de setembro de 2026 às 09:41 2 de 5"))
    }

    @Test
    fun `pendencia escrita pelo usuario nao e automatica`() {
        assertFalse(BankStatementRules.isAutomaticNote("Pedir o extrato ao Filipe"))
        assertTrue(BankStatementRules.isAutomaticNote("Não foi possível ler o PDF"))
    }

    @Test
    fun `so os extratos bancarios das duas contas sao conferidos`() {
        assertEquals("congregacao.extrato_bancario", BankStatementRules.categoryFor(AccountType.CONGREGACAO))
        assertEquals("manutencao.extrato_bancario", BankStatementRules.categoryFor(AccountType.MANUTENCAO))
        assertFalse(BankStatementRules.isStatementCategory("congregacao.extrato_betel"))
        assertNull(BankStatementRules.categoryFor(null))
    }

    private companion object {
        const val FILIPE = "Filipe da Silva Costa"
    }
}
