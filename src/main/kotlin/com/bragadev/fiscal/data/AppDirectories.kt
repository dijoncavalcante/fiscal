package com.bragadev.fiscal.data

import java.nio.file.Files
import java.nio.file.Path

/** Pastas e arquivos locais da aplicação (banco, backups, logs, controle de instância). Nada sai desta máquina. */
class AppDirectories(val dataDirectory: Path = defaultDataDirectory()) {
    val databaseFile: Path get() = dataDirectory.resolve(DATABASE_FILE_NAME)
    val backupDirectory: Path get() = dataDirectory.resolve(BACKUP_FOLDER_NAME)
    val logDirectory: Path get() = dataDirectory.resolve(LOG_FOLDER_NAME)

    /** Banco importado aguardando a próxima abertura do app para substituir o atual. */
    val pendingImportFile: Path get() = dataDirectory.resolve(PENDING_IMPORT_FILE_NAME)
    val instanceLockFile: Path get() = dataDirectory.resolve(LOCK_FILE_NAME)
    val activationSignalFile: Path get() = dataDirectory.resolve(ACTIVATION_FILE_NAME)

    fun ensureCreated() {
        Files.createDirectories(dataDirectory)
        Files.createDirectories(backupDirectory)
        Files.createDirectories(logDirectory)
    }

    companion object {
        private const val APP_FOLDER_NAME = "Fiscal"
        private const val DATABASE_FILE_NAME = "fiscal.db"
        private const val BACKUP_FOLDER_NAME = "backup"
        private const val LOG_FOLDER_NAME = "logs"
        private const val PENDING_IMPORT_FILE_NAME = "fiscal-importado.db"
        private const val LOCK_FILE_NAME = "fiscal.lock"
        private const val ACTIVATION_FILE_NAME = "abrir-janela.sinal"

        private fun defaultDataDirectory(): Path {
            val appData = System.getenv("APPDATA")
            val base = if (appData.isNullOrBlank()) Path.of(System.getProperty("user.home")) else Path.of(appData)
            return base.resolve(APP_FOLDER_NAME)
        }
    }
}
