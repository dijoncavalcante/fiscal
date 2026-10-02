package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.fakes.createFakePdf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileRepositoryImplTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val repository = FileRepositoryImpl()

    @Test
    fun `mover nunca sobrescreve o destino`() = runTest {
        val root = temp.root.toPath()
        val source = root.createFakePdf("a.pdf", content = "a")
        val target = root.createFakePdf("b.pdf", content = "b")

        val result = repository.move(source, target)

        assertEquals(Outcome.Failure(FileOperationError.DestinationAlreadyExists), result)
        assertTrue(Files.readString(target).contains("b"))
        assertTrue(Files.exists(source))
    }

    @Test
    fun `lista apenas pdfs incluindo subpastas`() = runTest {
        val root = temp.root.toPath()
        root.createFakePdf("um.pdf")
        root.resolve("sub").createFakePdf("dois.PDF")
        Files.writeString(root.resolve("planilha.xlsx"), "x")

        val documents = (repository.listPdfFiles(root) as Outcome.Success).value

        assertEquals(setOf("um.pdf", "dois.PDF"), documents.map { it.name }.toSet())
    }

    @Test
    fun `arquivo com extensao pdf mas sem assinatura nao e pdf`() = runTest {
        val fake = temp.root.toPath().resolve("falso.pdf").also { Files.writeString(it, "nao sou pdf") }
        assertEquals(false, repository.isPdf(fake))
    }
}
