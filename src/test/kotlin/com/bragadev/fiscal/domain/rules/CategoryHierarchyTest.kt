package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryHierarchyTest {
    private val hierarchy = CategoryHierarchy(DefaultCategories.all)

    @Test
    fun `arvore agrupa por conta e aninha subcategorias`() {
        val tree = hierarchy.tree()
        assertEquals(listOf(AccountType.CONGREGACAO, AccountType.MANUTENCAO), tree.map { it.accountType })

        val congregacao = tree.first()
        val remessa = congregacao.nodes.first { it.category.number == "5" }
        assertEquals(listOf("5.1"), remessa.children.map { it.category.number })
    }

    @Test
    fun `outros fica no final de cada conta`() {
        val (congregacao, manutencao) = hierarchy.tree()
        assertEquals("congregacao.outros", congregacao.nodes.last().category.id)
        assertEquals("10. Outros", congregacao.nodes.last().category.label)
        assertEquals("manutencao.outros", manutencao.nodes.last().category.id)
        assertEquals("6. Outros", manutencao.nodes.last().category.label)
        assertEquals(10, congregacao.nodes.size)
        assertEquals(6, manutencao.nodes.size)
    }

    @Test
    fun `pasta da manutencao mostra so manutencao com 6 outros no final`() {
        val tree = hierarchy.tree(AccountType.MANUTENCAO)
        assertEquals(listOf(AccountType.MANUTENCAO), tree.map { it.accountType })
        assertEquals("manutencao.outros", tree.single().nodes.last().category.id)
        assertTrue(tree.single().nodes.none { it.category.id == DefaultCategories.OUTROS_ID })
    }

    @Test
    fun `categoria de outra conta nao e permitida na pasta do mes`() {
        val extratoBetel = hierarchy.find("congregacao.extrato_betel")!!
        val outros = hierarchy.find(DefaultCategories.OUTROS_ID)!!
        assertFalse(hierarchy.isAllowedIn(extratoBetel, AccountType.MANUTENCAO))
        assertTrue(hierarchy.isAllowedIn(extratoBetel, AccountType.CONGREGACAO))
        // Nas duas contas, o "Outros" geral é substituído pelo "Outros" da conta (10 e 6).
        assertFalse(hierarchy.isAllowedIn(outros, AccountType.CONGREGACAO))
        assertFalse(hierarchy.isAllowedIn(outros, AccountType.MANUTENCAO))
        assertTrue(hierarchy.isAllowedIn(hierarchy.find("congregacao.outros")!!, AccountType.CONGREGACAO))
        assertTrue(hierarchy.isAllowedIn(hierarchy.find("manutencao.outros")!!, AccountType.MANUTENCAO))
        assertTrue(hierarchy.isAllowedIn(extratoBetel, null))
    }
}
