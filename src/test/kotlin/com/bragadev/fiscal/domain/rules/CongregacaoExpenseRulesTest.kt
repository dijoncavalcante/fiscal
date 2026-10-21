package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AmountProblemKind
import com.bragadev.fiscal.domain.model.DocumentContent
import com.bragadev.fiscal.domain.model.ExpenseRequirement.*
import com.bragadev.fiscal.domain.model.ExpenseRequirements
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Despesas da Conta da Congregação: assinatura de Andrey Alves da Costa (certificado ou selo gov.br completo),
 * comprovante Pix/TED sempre; cupom e valor só quando o nome do arquivo não isenta. Textos do OCR real.
 */
class CongregacaoExpenseRulesTest {
    private val andrey = "Andrey Alves da Costa"
    private val caseB = ExpenseRequirements(andrey)
    private val caseA = ExpenseRequirements(andrey, exemptBy = "Resolução")

    /** Comprovante real ("3. Despesa - Resolução Mensal", agosto/2026) com o selo lido pelo OCR. */
    private val pixWithStamp = "Comprovante de transferência 10 AGO 2026 10:30:32 Valor R\$ 800,00 " +
            "Documento assinado digitalmente ANDREY ALVES DA COSTA G UB Data: 10/08/2026 10:51:32-0300 " +
            "Verifique em https://validar.iti.gov.br Tipo de transferência Pix Id da transação: " +
            "E60746948202608101330M3943abcdeF"
    private val pixOnly = pixWithStamp.replace(Regex("Documento assinado.*validar.iti.gov.br"), "")
    private val andreyCertificate = "CN=ANDREY ALVES DA COSTA"

    private fun check(
        vararg pages: String,
        requirements: ExpenseRequirements = caseB,
        signers: List<String> = emptyList()
    ) =
        ExpenseDocumentRules.check(DocumentContent(pages.toList(), signers), requirements)

    @Test
    fun `caso A - isenta pelo nome exige so assinatura e comprovante, e o certo diz por que`() {
        val result = check(pixWithStamp, requirements = caseA)

        assertTrue(result.isValid, "faltou: ${result.missing}, valor: ${result.amountProblem}")
        assertEquals("Sem cupom: despesa isenta (\"Resolução\" no nome do arquivo)", result.amountEvidence)
    }

    @Test
    fun `caso B - sem isencao o cupom e obrigatorio`() {
        val result = check(pixWithStamp)

        assertEquals(setOf(FISCAL_DOCUMENT), result.missing)
        assertEquals("Falta cupom, nota fiscal ou recibo", ExpenseDocumentRules.issueNote(result))
    }

    @Test
    fun `caso B - com cupom o valor precisa ser igual`() {
        assertTrue(check("CUPOM FISCAL TOTAL R\$ 800,00", pixWithStamp).isValid)

        val different = check("CUPOM FISCAL TOTAL R\$ 750,00", pixWithStamp)
        assertEquals(AmountProblemKind.MISMATCH, different.amountProblem?.kind)
        assertEquals(
            "Valor do comprovante (R$ 800,00) diferente do cupom/nota (R$ 750,00): confira e troque o documento errado",
            ExpenseDocumentRules.issueNote(different),
        )
    }

    @Test
    fun `isenta continua exigindo comprovante e assinatura`() {
        val result = check("Orçamento de ônibus", requirements = caseA)

        assertEquals(setOf(PAYMENT_PROOF, SIGNATURE), result.missing)
        assertEquals(
            "Falta comprovante Pix/TED e assinatura de Andrey Alves da Costa",
            ExpenseDocumentRules.issueNote(result),
        )
    }

    @Test
    fun `assinatura pelo certificado digital, sem selo no texto`() {
        assertTrue(check(pixOnly, requirements = caseA, signers = listOf(andreyCertificate)).isValid)
    }

    @Test
    fun `selo sem o validar iti gov br nao vale, a data nao importa`() {
        val noSite = pixWithStamp.replace("Verifique em https://validar.iti.gov.br", "")
        val otherDate = pixWithStamp.replace("10/08/2026 10:51:32", "03/02/2027 08:00:00")

        assertEquals(setOf(SIGNATURE), check(noSite, requirements = caseA).missing)
        assertTrue(check(otherDate, requirements = caseA).isValid)
    }

    @Test
    fun `assinatura de outra pessoa nao vale para a Congregacao`() {
        val elvesStamp = pixWithStamp.replace("ANDREY ALVES DA COSTA", "ELVES LINCOLN MENEZES DE LIMA")

        assertEquals(setOf(SIGNATURE), check(elvesStamp, requirements = caseA).missing)
        assertEquals(
            setOf(SIGNATURE),
            check(pixOnly, requirements = caseA, signers = listOf("CN=ELVES LINCOLN MENEZES DE LIMA")).missing,
        )
    }

    @Test
    fun `sem responsavel configurado a pendencia diz onde configurar`() {
        val result = check(pixWithStamp, requirements = ExpenseRequirements(null, exemptBy = "Resolução"))

        assertEquals(setOf(SIGNATURE), result.missing)
        val note = ExpenseDocumentRules.issueNote(result)!!
        assertEquals(
            "Falta assinatura do responsável (configure quem assina em Configurações → Assinaturas das despesas)",
            note,
        )
        assertTrue(ExpenseDocumentRules.isAutomaticNote(note))
    }

    @Test
    fun `pendencia com qualquer nome de responsavel e automatica, a do usuario nao`() {
        assertTrue(ExpenseDocumentRules.isAutomaticNote("Falta assinatura de Andrey Alves da Costa"))
        assertTrue(ExpenseDocumentRules.isAutomaticNote("Falta comprovante Pix/TED e assinatura de Fulano de Tal"))
        assertTrue(ExpenseDocumentRules.isAutomaticNote("Falta cupom ou nota fiscal e assinatura de Elves Lincoln"))
        assertFalse(ExpenseDocumentRules.isAutomaticNote("Falta assinatura; trocar o cupom rasgado"))
        assertFalse(ExpenseDocumentRules.isAutomaticNote("Andrey vai assinar amanhã"))
    }

    @Test
    fun `isenta nao precisa ler mais nada depois do comprovante`() {
        assertTrue(ExpenseDocumentRules.hasEnoughText(DocumentContent(listOf(pixOnly)), caseA))
        assertFalse(ExpenseDocumentRules.hasEnoughText(DocumentContent(listOf(pixOnly)), caseB))
        assertNull(check(pixWithStamp, requirements = caseA).amountProblem)
    }
}
