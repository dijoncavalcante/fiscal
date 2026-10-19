package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OperationType
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.fakes.*
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Menu do botão direito na lista de documentos e no mês em edição: excluir (para a lixeira do FISCAL) e renomear, com Desfazer. */
class DocumentActionsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val downloads: Path by lazy { temp.newFolder("Downloads").toPath() }
    private val trash: Path by lazy { temp.root.toPath().resolve("Fiscal").resolve("lixeira") }
    private val may: Path by lazy {
        temp.newFolder("pendriver").toPath().resolve("CONTAS CONGREGAÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("3. TRIMESTRE Mar-Abr-Mai").resolve("3. MAIO").also(Files::createDirectories)
    }
    private val june: Path by lazy {
        may.parent.parent.resolve("4. TRIMESTRE Jun-Jul-Ago").resolve("1. JUNHO").also(Files::createDirectories)
    }

    private val fileRepository = FileRepositoryImpl()
    private val history = InMemoryHistoryRepository()
    private val flags = InMemoryFlagRepository()
    private val policy = EditablePeriodPolicy(YearMonth.of(2026, 6))
    private val mover by lazy { RecordedFileMover(fileRepository, history, NoOpDocumentRepository(), flags) }
    private val backups by lazy { TempBackupStorage(temp.newFolder("backup").toPath(), trash) }
    private val delete by lazy { DeleteDocumentUseCase(fileRepository, backups, mover, policy) }
    private val rename by lazy { RenameDocumentUseCase(fileRepository, mover, policy) }
    private val undo by lazy { UndoOperationUseCase(fileRepository, history, NoOpDocumentRepository(), flags, policy) }

    private fun trashFiles(): List<Path> = if (Files.isDirectory(trash)) Files.list(trash).use { it.toList() } else emptyList()

    // ---- Excluir ----

    @Test
    fun `excluir guarda o arquivo na lixeira do FISCAL e o desfazer devolve`() = runTest {
        val file = downloads.createFakePdf("Extrato junho.pdf", content = "conteudo original")

        val operation = (delete(file) as Outcome.Success).value

        assertFalse(Files.exists(file))
        assertEquals(OperationType.DELETE, operation.type)
        assertEquals(listOf(Path.of(operation.newPath)), trashFiles())
        assertTrue(operation.newName.endsWith("Extrato junho.pdf"))

        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.exists(file))
        assertTrue("conteudo original" in Files.readString(file))
        assertTrue(trashFiles().isEmpty())
    }

    @Test
    fun `excluir imagem e a pendencia volta junto com o desfazer`() = runTest {
        val photo = Files.writeString(downloads.resolve("WhatsApp Image 2026-06-03.jpg"), "foto")
        flags.set(photo, "Conferir valor")

        val operation = (delete(photo) as Outcome.Success).value
        assertTrue(flags.flags.keys.none { it == photo })

        undo(operation.id)
        assertEquals("Conferir valor", flags.flags[photo])
    }

    @Test
    fun `excluir de mes bloqueado e recusado e nada muda`() = runTest {
        val file = may.createFakePdf("1. Folha de Contas (S-26).pdf")

        assertIs<FileOperationError.MonthLocked>(delete.check(file))
        assertIs<Outcome.Failure>(delete(file))
        assertTrue(Files.exists(file))
        assertTrue(history.operations.isEmpty())
    }

    @Test
    fun `excluir arquivo do mes em edicao vai para a lixeira e o desfazer devolve com a pendencia`() = runTest {
        val file = june.createFakePdf("3. Despesa - Material de limpeza.pdf")
        flags.set(file, "Falta nota fiscal")

        val operation = (delete(file) as Outcome.Success).value
        assertFalse(Files.exists(file))

        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.exists(file))
        assertEquals("Falta nota fiscal", flags.flags[file])
    }

    @Test
    fun `renomear arquivo sem numero no mes em edicao fica na pasta do mes`() = runTest {
        val file = june.createFakePdf("scan001.pdf")

        val operation = (rename(file, "8. Extrato Bancário") as Outcome.Success).value

        assertEquals(june.resolve("8. Extrato Bancário.pdf"), Path.of(operation.newPath))
        assertTrue(Files.exists(june.resolve("8. Extrato Bancário.pdf")))
    }

    @Test
    fun `excluir arquivo que ja sumiu avisa`() = runTest {
        assertEquals(Outcome.Failure(FileOperationError.FileNotFound), delete(downloads.resolve("sumiu.pdf")))
    }

    @Test
    fun `dois arquivos com o mesmo nome podem ir para a lixeira`() = runTest {
        val first = downloads.createFakePdf("scan.pdf")
        assertIs<Outcome.Success<*>>(delete(first))
        val second = downloads.createFakePdf("scan.pdf")
        assertIs<Outcome.Success<*>>(delete(second))

        assertEquals(2, trashFiles().size)
    }

    // ---- Renomear ----

    @Test
    fun `renomear mantem a extensao e pode ser desfeito`() = runTest {
        val photo = Files.writeString(downloads.resolve("WhatsApp Image 2026-06-03.jpg"), "foto")

        val operation = (rename(photo, "Recibo da conta de luz") as Outcome.Success).value

        val renamed = downloads.resolve("Recibo da conta de luz.jpg")
        assertTrue(Files.exists(renamed))
        assertEquals(OperationType.RENAME, operation.type)
        assertIs<Outcome.Success<*>>(undo(operation.id))
        assertTrue(Files.exists(photo))
        assertFalse(Files.exists(renamed))
    }

    @Test
    fun `renomear nunca substitui outro arquivo`() = runTest {
        val file = downloads.createFakePdf("scan001.pdf")
        downloads.createFakePdf("Extrato Bancário.pdf")

        assertEquals(Outcome.Failure(FileOperationError.DestinationAlreadyExists), rename.plan(file, "extrato bancário"))
        assertTrue(Files.exists(file))
    }

    @Test
    fun `renomear recusa nome vazio, invalido ou igual ao atual`() = runTest {
        val file = downloads.createFakePdf("scan001.pdf")

        assertEquals(Outcome.Failure(FileOperationError.NameRequired), rename.plan(file, "   "))
        assertEquals(Outcome.Failure(FileOperationError.InvalidFileName), rename.plan(file, "a:b"))
        assertEquals(Outcome.Failure(FileOperationError.AlreadyInPlace), rename.plan(file, "scan001.pdf"))
        // Só maiúsculas/minúsculas: não conflita com o próprio arquivo.
        assertEquals(Outcome.Success(downloads.resolve("SCAN001.pdf")), rename.plan(file, "SCAN001"))
    }

    @Test
    fun `renomear em mes bloqueado e recusado`() = runTest {
        val file = may.createFakePdf("8. Extrato Bancário.pdf")

        assertIs<FileOperationError.MonthLocked>((rename.plan(file, "8. Extrato") as Outcome.Failure).error)
    }
}
