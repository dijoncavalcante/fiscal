package com.bragadev.fiscal.domain.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DescribedSequenceNamingTest {
    private val despesas = DefaultCategories.all.first { it.id == "congregacao.despesas" }

    private fun next(existing: Set<String>, description: String = "xxx") =
        CategoryNaming.suggestedName(despesas, existing, description)

    @Test
    fun `primeira despesa do mes usa o numero da categoria`() {
        assertEquals("3. Despesa - Compra de cartazes.pdf", next(emptySet(), "Compra de cartazes"))
    }

    @Test
    fun `despesas seguintes recebem 3_1 3_2`() {
        assertEquals("3.1 Despesa - yyy.pdf", next(setOf("3. Despesa - xxx.pdf"), "yyy"))
        assertEquals("3.2 Despesa - xyz.pdf", next(setOf("3. Despesa - xxx.pdf", "3.1 Despesa - yyy.pdf"), "xyz"))
    }

    @Test
    fun `nunca reaproveita numero e considera nomes reais fora do padrao`() {
        assertEquals("3.3 Despesa - x.pdf", next(setOf("3 Despesa - Resolução Manutenção.pdf", "3.2 Despesa - a.pdf"), "x"))
        assertEquals(
            "3.2 Despesa - x.pdf",
            next(setOf("3. Despesa -RESOLUÇÃO MENSAL.pdf", "3.1 Despesa - ônibus para o congresso.pdf", "8. Extrato Bancário.pdf"), "x"),
        )
    }

    @Test
    fun `outras categorias e numeros parecidos nao contam`() {
        assertEquals("3. Despesa - x.pdf", next(setOf("30. Algo.pdf", "1. Folha de Contas.pdf", "13 Outra.pdf"), "x"))
    }

    @Test
    fun `descricao vazia nao gera nome`() {
        assertNull(next(emptySet(), "   "))
    }

    @Test
    fun `editar despesa existente mantem o numero`() {
        val name = CategoryNaming.suggestedName(
            despesas, setOf("3. Despesa - a.pdf", "3.2 Despesa - c.pdf"), "nova descrição", currentFileName = "3.1 Despesa - b.pdf",
        )
        assertEquals("3.1 Despesa - nova descrição.pdf", name)
    }

    @Test
    fun `sugestao de descricao vem do nome do arquivo sem repetir despesa`() {
        assertEquals(
            "Compra de cartazes para o display assinado (1)",
            DescribedSequenceNaming.suggestDescription(despesas, "Despesa_-_Compra_de_cartazes_para_o_display_assinado (1).pdf"),
        )
        assertEquals("ônibus para o congresso", DescribedSequenceNaming.suggestDescription(despesas, "3.1 Despesa - ônibus para o congresso.pdf"))
        assertEquals("Resolução Manutenção - AGOSTO 2026 assinado", DescribedSequenceNaming.suggestDescription(despesas, "Resolução Manutenção - AGOSTO 2026 assinado.pdf"))
    }

    @Test
    fun `outros da manutencao segue 6 6_1 6_2`() {
        val outros = DefaultCategories.all.first { it.id == "manutencao.outros" }
        assertEquals("6. Outros - xxx.pdf", CategoryNaming.suggestedName(outros, emptySet(), "xxx"))
        assertEquals("6.1 Outros - yyyy.pdf", CategoryNaming.suggestedName(outros, setOf("6. Outros - xxx.pdf"), "yyyy"))
        assertEquals(
            "6.2 Outros - z.pdf",
            CategoryNaming.suggestedName(outros, setOf("6. Outros - xxx.pdf", "6.1 Outros - yyyy.pdf", "5. Extrato Bancário.pdf"), "z"),
        )
        assertEquals("Nota fiscal", DescribedSequenceNaming.suggestDescription(outros, "Outros - Nota fiscal.pdf"))
    }
}
