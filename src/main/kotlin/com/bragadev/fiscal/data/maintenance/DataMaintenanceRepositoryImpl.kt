package com.bragadev.fiscal.data.maintenance

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.filesystem.FileErrorMapper
import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.domain.model.BackupStats
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.DataMaintenanceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DataMaintenanceRepositoryImpl(
    private val directories: AppDirectories,
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataMaintenanceRepository {

    override suspend fun backupStats(): BackupStats = withContext(ioDispatcher) {
        val files = backupFiles()
        BackupStats(files.size, files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) })
    }

    override suspend fun deleteBackupsOlderThan(days: Int): Int = withContext(ioDispatcher) {
        val limit = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val old = backupFiles().filter { runCatching { Files.getLastModifiedTime(it).toInstant() < limit }.getOrDefault(false) }
        val deleted = old.count { runCatching { Files.delete(it) }.isSuccess }
        AppLog.info("Limpeza de backups com mais de $days dias: $deleted de ${old.size} apagados")
        deleted
    }

    override suspend fun exportData(target: Path): Outcome<Unit> {
        if (Files.exists(target)) return Outcome.Failure(FileOperationError.DestinationAlreadyExists)
        return try {
            // VACUUM INTO gera uma cópia consistente mesmo com o app em uso.
            database.use { createStatement().use { it.executeUpdate("VACUUM INTO '${target.toString().replace("'", "''")}'") } }
            AppLog.info("Dados exportados para $target")
            Outcome.Success(Unit)
        } catch (error: Exception) {
            AppLog.error("Falha ao exportar dados para $target", error)
            Outcome.Failure(FileErrorMapper.map(error))
        }
    }

    override suspend fun stageImport(source: Path): Outcome<Unit> = withContext(ioDispatcher) {
        if (!Files.isRegularFile(source)) return@withContext Outcome.Failure(FileOperationError.FileNotFound)
        if (!isFiscalDatabase(source)) return@withContext Outcome.Failure(FileOperationError.InvalidBackupFile)
        try {
            Files.copy(source, directories.pendingImportFile, StandardCopyOption.REPLACE_EXISTING)
            AppLog.info("Importação preparada a partir de $source (aplicada na próxima abertura)")
            Outcome.Success(Unit)
        } catch (error: Exception) {
            Outcome.Failure(FileErrorMapper.map(error))
        }
    }

    private fun backupFiles(): List<Path> {
        val folder = directories.backupDirectory
        if (!Files.isDirectory(folder)) return emptyList()
        return Files.list(folder).use { entries -> entries.filter { Files.isRegularFile(it) }.toList() }
    }

    private fun isFiscalDatabase(file: Path): Boolean = runCatching {
        val readOnly = SQLiteConfig().apply { setReadOnly(true) }.toProperties()
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}", readOnly).use { connection ->
            val tables = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.toSet()
                }
            }
            REQUIRED_TABLES.all { it in tables }
        }
    }.getOrDefault(false)

    companion object {
        private val REQUIRED_TABLES = setOf("settings", "categories", "file_operations")
        private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /**
         * Chamado ao abrir o app, antes de abrir o banco: se houver uma importação pendente, guarda o banco
         * atual como cópia ("fiscal-antes-da-importacao-<data>.db") e coloca o importado no lugar.
         */
        fun applyPendingImport(directories: AppDirectories) {
            val pending = directories.pendingImportFile
            if (!Files.exists(pending)) return
            try {
                val current = directories.databaseFile
                if (Files.exists(current)) {
                    val keep = directories.dataDirectory.resolve("fiscal-antes-da-importacao-${LocalDateTime.now().format(stamp)}.db")
                    Files.move(current, keep)
                    AppLog.info("Banco anterior guardado em $keep")
                }
                Files.move(pending, current)
                AppLog.info("Importação aplicada")
            } catch (error: Exception) {
                AppLog.error("Falha ao aplicar a importação; o banco atual foi mantido", error)
            }
        }
    }
}
