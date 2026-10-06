package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.fakes.createFakePdf
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
    fun `lista apenas pdfs da propria pasta`() = runTest {
        val root = temp.root.toPath()
        root.createFakePdf("um.pdf")
        root.resolve("sub").createFakePdf("dois.PDF")
        root.createFakePdf("._um.pdf")
        Files.writeString(root.resolve("planilha.xlsx"), "x")

        val documents = (repository.listPdfFiles(root) as Outcome.Success).value

        assertEquals(setOf("um.pdf"), documents.map { it.name }.toSet())
    }

    @Test
    fun `arquivo com extensao pdf mas sem assinatura nao e pdf`() = runTest {
        val fake = temp.root.toPath().resolve("falso.pdf").also { Files.writeString(it, "nao sou pdf") }
        assertEquals(false, repository.isPdf(fake))
    }

    @Test
    fun `avisa quando um arquivo muda na pasta`() = runBlocking {
        val root = temp.root.toPath()
        val changes = async { withTimeout(10_000) { repository.watch(root).first() } }
        delay(500)
        root.createFakePdf("novo.pdf")

        assertEquals(Unit, changes.await())
    }

    @Test
    fun `lista de documentos inclui imagens jpeg e png`() = runTest {
        val root = temp.root.toPath()
        root.createFakePdf("um.pdf")
        Files.writeString(root.resolve("foto.JPG"), "x")
        Files.writeString(root.resolve("print.png"), "x")
        Files.writeString(root.resolve("planilha.xlsx"), "x")
        Files.writeString(root.resolve("._foto.jpg"), "x")

        val documents = (repository.listDocuments(root) as Outcome.Success).value

        assertEquals(setOf("um.pdf", "foto.JPG", "print.png"), documents.map { it.name }.toSet())
        assertEquals(setOf("foto.JPG", "print.png"), documents.filter { it.isImage }.map { it.name }.toSet())
    }
}
