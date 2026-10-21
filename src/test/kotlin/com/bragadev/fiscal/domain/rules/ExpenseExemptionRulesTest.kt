package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AppSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Isenção de cupom pelo nome do arquivo, com os nomes reais das despesas da Congregação (2025-2026). */
class ExpenseExemptionRulesTest {
    private val terms = AppSettings.DEFAULT_EXPENSE_EXEMPT_TERMS

    private fun exempt(name: String) = ExpenseExemptionRules.exemptBy(name, terms)

    @Test
    fun `despesas reais isentas e o termo que dispensou o cupom`() {
        val expected = mapOf(
            "3.1 Pagamento ônibus Assembleia.pdf" to "Ônibus para a assembleia",
            "3. Despesa - Pagamento onibus assembleia.pdf" to "Ônibus para a assembleia",
            "3.1 Despesa - ônibus para o congresso.pdf" to "Ônibus para o congresso",
            "3.2 Resolução Manutenção.pdf" to "Resolução",
            "3. Resolução Conta Manutenção.pdf" to "Resolução",
            "3.1 Despesa - Resolução mensal.pdf" to "Resolução",
            "3 Despesa - Resolução Manutenção.pdf" to "Resolução",
            "3. Despesa -RESOLUÇÃO MENSAL COMISSÃO DE FUNCIONAMENTO.pdf" to "Resolução",
            "3. Despesa - Resolução Mensal.pdf" to "Resolução",
            "3.1 Despesa - Objetivos especificos da manutenção.pdf" to "Objetivos específicos",
            "3.4 Despesa - Objetivos especificos.pdf" to "Objetivos específicos",
            "3.1 Despesa- OBJETIVOS ESPECIFICOS.pdf" to "Objetivos específicos",
            "3.1 Despesa - Donativos para conta da Manutenção - SETEMBRO 2025.pdf" to "Donativo",
            "3.1 DONATIVO COMISSÃO DE FUNCIONAMENTO.pdf" to "Donativo",
            "3.2 DONATIVO METADE DO VALOR DA VTM.pdf" to "Donativo",
            "3.3 DESPESA DONATIVOS VTM.pdf" to "Donativo",
            "3.2 REEMBOLSO PARCIAL - LIMPEZA AR CONDICIONADO.pdf" to "Reembolso",
            "3.2 Despesa - Reembolso Sup. Circuito.pdf" to "Reembolso",
            "3.1 PAGAMENTO DEPÓSITO DE PUBLICAÇÕES.pdf" to "Depósito de publicações",
        )

        expected.forEach { (name, term) -> assertEquals(term, exempt(name), name) }
    }

    @Test
    fun `despesas reais que exigem cupom`() {
        listOf(
            "3. Despesa - Arranjo Novo Projetor.pdf",
            "3 Despesa - Cópias de chaves do S. R.pdf",
            "3. Despesa - Cópia de chave do salão do Reino.pdf",
            "3.2 Despesa da visita - Sidney Anselmo.pdf",
            "3.3 Despesa da visita - Cleiton Silva.pdf",
            "3.1 Despesa - Compra de cartazes para o display.pdf",
            // Cita "congresso", mas não é o ônibus.
            "3.1 Despesa - Impressao convite do congresso.pdf",
        ).forEach { assertNull(exempt(it), it) }
    }

    @Test
    fun `singular e plural, acento e ordem das palavras nao importam`() {
        assertEquals("Depósito de publicações", exempt("3. Despesa - Publicação depósito.pdf"))
        assertEquals("Ônibus para a assembleia", exempt("3. ASSEMBLEIAS - ONIBUS.pdf"))
    }

    @Test
    fun `termo novo das Configuracoes vale e sem termos tudo exige cupom`() {
        assertEquals("Aluguel", ExpenseExemptionRules.exemptBy("3. Despesa - Aluguel do salão.pdf", terms + "Aluguel"))
        assertNull(ExpenseExemptionRules.exemptBy("3.1 Despesa - Resolução mensal.pdf", emptyList()))
        // Termo só com palavras de ligação nunca vale para tudo.
        assertNull(ExpenseExemptionRules.exemptBy("3. Despesa - Projetor.pdf", listOf("de", " ")))
    }
}
