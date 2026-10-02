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
        val tree = hierarchy.tree()
        tree.forEach { group -> assertEquals(DefaultCategories.OUTROS_ID, group.nodes.last().category.id) }
        assertEquals(10, tree[0].nodes.size)
        assertEquals(6, tree[1].nodes.size)
    }

    @Test
    fun `pasta da manutencao mostra so manutencao com outros no final`() {
        val tree = hierarchy.tree(AccountType.MANUTENCAO)
        assertEquals(listOf(AccountType.MANUTENCAO), tree.map { it.accountType })
        assertEquals(DefaultCategories.OUTROS_ID, tree.single().nodes.last().category.id)
    }

    @Test
    fun `categoria de outra conta nao e permitida na pasta do mes`() {
        val extratoBetel = hierarchy.find("congregacao.extrato_betel")!!
        val outros = hierarchy.find(DefaultCategories.OUTROS_ID)!!
        assertFalse(hierarchy.isAllowedIn(extratoBetel, AccountType.MANUTENCAO))
        assertTrue(hierarchy.isAllowedIn(extratoBetel, AccountType.CONGREGACAO))
        assertTrue(hierarchy.isAllowedIn(outros, AccountType.MANUTENCAO))
        assertTrue(hierarchy.isAllowedIn(extratoBetel, null))
    }
}
