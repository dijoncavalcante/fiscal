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
        assertEquals(listOf(AccountType.CONGREGACAO, AccountType.MANUTENCAO, AccountType.OUTROS), tree.map { it.accountType })

        val congregacao = tree.first()
        assertEquals(9, congregacao.nodes.size)
        val remessa = congregacao.nodes.first { it.category.number == "5" }
        assertEquals(listOf("5.1"), remessa.children.map { it.category.number })
        assertEquals(5, tree[1].nodes.size)
    }

    @Test
    fun `pasta da manutencao mostra so manutencao e outros`() {
        val tree = hierarchy.tree(AccountType.MANUTENCAO)
        assertEquals(listOf(AccountType.MANUTENCAO, AccountType.OUTROS), tree.map { it.accountType })
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
