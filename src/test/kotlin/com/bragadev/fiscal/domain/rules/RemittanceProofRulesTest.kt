package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseRequirement
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "5.1 Comprovante Remessa" da Conta da Congregação, com o texto como o OCR lê os comprovantes reais do Nubank
 * (todos digitalizados; julho e agosto/2026 com a assinatura digital do Andrey).
 */
class RemittanceProofRulesTest {
    /** "5.1 Comprovante Remessa.pdf" de agosto/2026 pelo OCR ("Valor ns-975,00", "CNPJ $3765687000124"). */
    private val augustOcr = """
        nu
        Comprovante de
        transferéncia
        31 AGO 2026 - 11:08:47
        Documento assinado digitalmente
        Valor ns-975,00  QOwlbr Moesoncost
        Verifique em https://validariti.gov.br
        Tipo de transferéncia Pix
        Destino
        N Associacao Torre de Vigia de
        ome Biblias e Tratados
        CNPJ ${'$'}3765687000124
        Instituigao BCO BRADESCO'S.A.
        Agéncia 3372
        Conta 40100-5
        Tipo de conta Conta corrente
        Origem
        Nome Filipe da Silva Costa
        Instituigao NU PAGAMENTOS - IP
        Agéncia 0001
        Conta 15077760-0
        CPF +99369.732 - +
        Nu Pagamentos §.A. - Instituig¢ao de
        Pagamento
        CNPJ 18.236,120/0001-58
    """.trimIndent()

    private fun proof(
        text: String = augustOcr,
        signers: List<String> = listOf("CN=ANDREY ALVES DA COSTA:12345678900")
    ) =
        DocumentContent(listOf(text), signers = signers, ocrPages = setOf(0))

    private val remittance = TransferRecordRules.Remittance(listOf("5. Remessa Betel.pdf"), BigDecimal("975.00"))
    private val to62 = TransferRecordRules.TransferRecord(listOf("4. TO-62.pdf"), BigDecimal("975.00"), fromOcr = true)

    private fun check(
        content: DocumentContent = proof(),
        signer: String? = ANDREY,
        remittance: TransferRecordRules.Remittance = this.remittance,
        transferRecord: TransferRecordRules.TransferRecord = to62,
    ) = RemittanceProofRules.check(content, signer, remittance, transferRecord)

    @Test
    fun `comprovante real de agosto passa`() {
        val result = check()
        assertTrue(result.check.isValid, result.note)
        assertNull(result.note)
        assertEquals(
            "R$ 975,00 de Filipe da Silva Costa (Nubank) para Associação Torre de Vigia de Bíblias e Tratados, " +
                    "igual à remessa e ao TO-62; assinado por $ANDREY",
            result.check.amountEvidence,
        )
    }

    @Test
    fun `sem a assinatura do Andrey vira pendencia`() {
        val result = check(proof(signers = emptyList()))
        assertEquals(setOf(ExpenseRequirement.SIGNATURE), result.check.missing)
        assertEquals("Falta assinatura digital (gov.br) de $ANDREY", result.note)

        val notConfigured = check(signer = null)
        assertEquals(
            "Falta assinatura digital (gov.br) do responsável das despesas da Congregação " +
                    "(configure em Configurações → Assinaturas das despesas e extratos)",
            notConfigured.note,
        )
        listOf(result.note!!, notConfigured.note!!).forEach { assertTrue(RemittanceProofRules.isAutomaticNote(it)) }
    }

    @Test
    fun `valor diferente da remessa e do TO-62 vira pendencia com os valores`() {
        val result = check(
            remittance = remittance.copy(total = BigDecimal("960.00")),
            transferRecord = to62.copy(sent = BigDecimal("960.00")),
        )
        assertEquals(setOf(ExpenseRequirement.TRANSFER_TOTAL), result.check.missing)
        assertEquals(
            "Valor da transferência (R$ 975,00) é diferente do Total da Remessa Betel (R$ 960,00): confira à mão " +
                    "(foto); Valor da transferência (R$ 975,00) é diferente do TOTAL DE FUNDOS ENVIADOS do TO-62 " +
                    "(R$ 960,00): confira à mão (foto)",
            result.note,
        )
        assertTrue(RemittanceProofRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `sem documento na remessa ou no TO-62 vira pendencia`() {
        val result = check(
            remittance = TransferRecordRules.Remittance(emptyList(), null),
            transferRecord = TransferRecordRules.TransferRecord(emptyList(), null, fromOcr = false),
        )
        assertEquals(
            "Valor da transferência (R$ 975,00) não pôde ser conferido com o Total da Remessa Betel: \"5. Remessa " +
                    "Betel\" não tem nenhum documento neste mês; Valor da transferência (R$ 975,00) não pôde ser " +
                    "conferido com o TOTAL DE FUNDOS ENVIADOS do TO-62: \"4. TO-62\" não tem nenhum documento neste mês",
            result.note,
        )
        assertTrue(RemittanceProofRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `comprovante de outro banco ou com outra conta vira pendencia`() {
        // PDF do banco com texto (não foto): pede para trocar.
        val otherBankText = """
            Comprovante de transferência 31 AGO 2026 Valor R$ 975,00 Destino Nome Associação Torre de Vigia de
            Bíblias e Tratados CNPJ 33755687000124 Instituição BCO BRADESCO S.A. Agência 3372 Conta 40100-5
            Tipo de conta Conta corrente Origem Nome Filipe da Silva Costa Instituição BANCO INTER Agência 0001
            Conta 15077760-0 CPF ***.369.732-**
        """.trimIndent()
        val otherBank = check(DocumentContent(listOf(otherBankText), signers = listOf("CN=ANDREY ALVES DA COSTA")))
        assertEquals(setOf(ExpenseRequirement.PROOF_DATA), otherBank.check.missing)
        assertEquals(
            "Comprovante não é do Nubank (Nu Pagamentos): troque pelo comprovante da remessa; Comprovante sem os " +
                    "dados da remessa (não tem origem Nu Pagamentos): troque pelo comprovante certo",
            otherBank.note,
        )

        val otherAccount = check(proof(augustOcr.replace("Conta 40100-5", "Conta 55555-1")))
        assertEquals(
            "Dados da remessa não confirmados na foto (não li destino CNPJ 33755687000124 e destino Conta 40100-5): " +
                    "confira à mão",
            otherAccount.note,
        )
        listOf(otherBank.note!!, otherAccount.note!!).forEach { assertTrue(RemittanceProofRules.isAutomaticNote(it)) }
    }

    @Test
    fun `valor nao lido pede para conferir a mao`() {
        val result = check(proof(augustOcr.replace("Valor ns-975,00", "Valor ns-")))
        assertEquals(
            "Valor da transferência não foi lido: confira à mão se é igual ao Total da Remessa Betel (R$ 975,00)",
            result.note,
        )
        assertTrue(RemittanceProofRules.isAutomaticNote(result.note!!))
    }

    @Test
    fun `valor com o cifrao lido como 3 pelo OCR`() {
        // Março/2026: "Valor R3 866,10".
        assertEquals(BigDecimal("866.10"), DonationRules.findAmount("Valor R3 866,10"))
        assertEquals(BigDecimal("975.00"), DonationRules.findAmount("Valor ns-975,00"))
    }

    private companion object {
        const val ANDREY = "Andrey Alves da Costa"
    }
}
