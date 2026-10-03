package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.DocumentSort
import org.junit.Test
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals

class DocumentSortingTest {
    private fun doc(name: String, minute: Long) = Document(Path.of(name), 0, Instant.ofEpochSecond(minute * 60))

    private val documents = listOf(doc("b.pdf", 1), doc("c.pdf", 3), doc("A.pdf", 2))

    @Test
    fun `mais recentes primeiro como no explorer`() {
        assertEquals(listOf("c.pdf", "A.pdf", "b.pdf"), DocumentSorting.apply(documents, DocumentSort.MODIFIED_NEWEST_FIRST).map { it.name })
    }

    @Test
    fun `por nome sem diferenciar maiusculas`() {
        assertEquals(listOf("A.pdf", "b.pdf", "c.pdf"), DocumentSorting.apply(documents, DocumentSort.NAME).map { it.name })
    }

    @Test
    fun `busca filtra pelo nome`() {
        assertEquals(listOf("A.pdf"), DocumentSorting.apply(documents, DocumentSort.NAME, " a.p ").map { it.name })
    }
}
