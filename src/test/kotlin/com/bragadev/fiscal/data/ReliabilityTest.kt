package com.bragadev.fiscal.data

import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.database.SettingsDao
import com.bragadev.fiscal.data.filesystem.SafeFileMover
import com.bragadev.fiscal.data.instance.SingleInstance
import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.data.maintenance.DataMaintenanceRepositoryImpl
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileInputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Uma cópia só, log, mover entre unidades com verificação, backups e exportar/importar dados. */
class ReliabilityTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val root: Path by lazy { temp.root.toPath() }

    // ---- Uma cópia só ----

    @Test
    fun `segunda copia nao consegue a trava e avisa a primeira`() = runBlocking {
        val lock = root.resolve("fiscal.lock")
        val signal = root.resolve("abrir-janela.sinal")
        val first = SingleInstance(lock, signal)
        val second = SingleInstance(lock, signal)

        assertTrue(first.tryAcquire())
        assertFalse(second.tryAcquire())

        val activation = async { withTimeout(10_000) { first.activationRequests().first() } }
        delay(500)
        second.requestActivation()
        assertEquals(Unit, activation.await())

        first.release()
        assertTrue(second.tryAcquire())
        second.release()
    }

    // ---- Mover entre unidades ----

    private val crossVolume = SafeFileMover(onDifferentVolumes = { _, _ -> true })

    @Test
    fun `entre unidades copia confere e so entao apaga o original`() {
        val source = root.resolve("pc").also(Files::createDirectories).resolve("a.pdf")
        Files.write(source, ByteArray(300_000) { (it % 251).toByte() })
        val original = Files.readAllBytes(source)
        val target = root.resolve("pendrive").resolve("mes").resolve("8. Extrato Bancário.pdf")

        crossVolume.move(source, target)

        assertContentEquals(original, Files.readAllBytes(target))
        assertFalse(Files.exists(source))
        assertTrue(Files.list(target.parent).use { files -> files.noneMatch { it.fileName.toString().startsWith("~fiscal-") } })
    }

    @Test
    fun `entre unidades nunca sobrescreve`() {
        val source = Files.writeString(root.resolve("a.pdf"), "novo")
        val target = Files.writeString(root.resolve("b.pdf"), "existente")

        assertFailsWith<FileAlreadyExistsException> { crossVolume.move(source, target) }

        assertEquals("existente", Files.readString(target))
        assertEquals("novo", Files.readString(source))
    }

    @Test
    fun `original aberto em outro programa fica intacto e a copia e desfeita`() {
        val source = Files.writeString(root.resolve("aberto.pdf"), "conteudo")
        val target = root.resolve("destino").resolve("aberto.pdf")

        FileInputStream(source.toFile()).use {
            // No Windows, um arquivo aberto assim não pode ser apagado.
            runCatching { crossVolume.move(source, target) }
        }

        assertTrue(Files.exists(source))
        assertEquals("conteudo", Files.readString(source))
        assertFalse(Files.exists(target))
    }

    // ---- Log ----

    @Test
    fun `log grava em arquivo e o diagnostico le o final`() {
        AppLog.init(root.resolve("logs"))
        AppLog.info("teste de registro 123")

        assertTrue(AppLog.tail(10).any { "teste de registro 123" in it })
    }

    // ---- Backups e cópia de segurança ----

    private fun maintenance(): Pair<DataMaintenanceRepositoryImpl, AppDirectories> {
        val directories = AppDirectories(root.resolve("Fiscal")).apply { ensureCreated() }
        return DataMaintenanceRepositoryImpl(directories, Database(directories.databaseFile)) to directories
    }

    @Test
    fun `limpeza apaga so backups mais antigos que o prazo`() = runTest {
        val (repository, directories) = maintenance()
        val old = Files.writeString(directories.backupDirectory.resolve("antigo.pdf"), "x")
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(100, ChronoUnit.DAYS)))
        val recent = Files.writeString(directories.backupDirectory.resolve("recente.pdf"), "x")

        assertEquals(2, repository.backupStats().fileCount)
        assertEquals(1, repository.deleteBackupsOlderThan(90))

        assertFalse(Files.exists(old))
        assertTrue(Files.exists(recent))
    }

    @Test
    fun `exportar e importar dados entre computadores`() = runTest {
        val (repository, directories) = maintenance()
        val database = Database(directories.databaseFile)
        SettingsDao(database).putAll(mapOf("month_folder" to "D:\\pendriver\\1. JUNHO"))
        val exportFile = root.resolve("fiscal-dados.db")

        assertEquals(Outcome.Success(Unit), DataMaintenanceRepositoryImpl(directories, database).exportData(exportFile))
        assertEquals(Outcome.Failure(FileOperationError.DestinationAlreadyExists), repository.exportData(exportFile))

        val notDatabase = Files.writeString(root.resolve("qualquer.db"), "não é banco")
        assertEquals(Outcome.Failure(FileOperationError.InvalidBackupFile), repository.stageImport(notDatabase))
        assertEquals(Outcome.Success(Unit), repository.stageImport(exportFile))
        database.close()

        // Na próxima abertura: o banco atual é guardado e o importado entra no lugar.
        DataMaintenanceRepositoryImpl.applyPendingImport(directories)
        assertFalse(Files.exists(directories.pendingImportFile))
        assertTrue(Files.list(directories.dataDirectory).use { files -> files.anyMatch { it.fileName.toString().startsWith("fiscal-antes-da-importacao") } })
        val imported = Database(directories.databaseFile)
        assertEquals("D:\\pendriver\\1. JUNHO", SettingsDao(imported).getAll()["month_folder"])
        imported.close()
    }
}
