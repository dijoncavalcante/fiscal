package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import org.junit.Test
import kotlin.test.assertEquals

class CategoryHierarchyTest {
    private val hierarchy = CategoryHierarchy(DefaultCategories.all)

    @Test
    fun `subcategoria gera caminho completo com a categoria pai`() {
        val comprovante = hierarchy.find("congregacao.comprovante_remessa")!!
        assertEquals(
            listOf("Conta da Congregação", "5. Remessa Betel", "5.1 Comprovante Remessa"),
            hierarchy.folderSegments(comprovante),
        )
    }

    @Test
    fun `categoria raiz fica dentro da pasta da conta`() {
        val extrato = hierarchy.find("congregacao.extrato_bancario")!!
        assertEquals(listOf("Conta da Congregação", "8. Extrato Bancário"), hierarchy.folderSegments(extrato))
    }

    @Test
    fun `outros fica direto na pasta raiz`() {
        val outros = hierarchy.find(DefaultCategories.OUTROS_ID)!!
        assertEquals(listOf("Outros"), hierarchy.folderSegments(outros))
    }

    @Test
    fun `arvore agrupa por conta e aninha subcategorias`() {
        val tree = hierarchy.tree()
        assertEquals(listOf(AccountType.CONGREGACAO, AccountType.MANUTENCAO, AccountType.OUTROS), tree.map { it.accountType })

        val congregacao = tree.first()
        assertEquals(9, congregacao.nodes.size)
        val remessa = congregacao.nodes.first { it.category.number == "5" }
        assertEquals(listOf("5.1"), remessa.children.map { it.category.number })
        assertEquals(5, tree[1].nodes.size)
    }
}
