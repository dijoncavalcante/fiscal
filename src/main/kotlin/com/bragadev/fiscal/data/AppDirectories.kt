package com.bragadev.fiscal.data

import java.nio.file.Files
import java.nio.file.Path

/** Pastas locais da aplicação (banco de dados e backups). Nada sai desta máquina. */
class AppDirectories(val dataDirectory: Path = defaultDataDirectory()) {
    val databaseFile: Path get() = dataDirectory.resolve(DATABASE_FILE_NAME)
    val backupDirectory: Path get() = dataDirectory.resolve(BACKUP_FOLDER_NAME)

    fun ensureCreated() {
        Files.createDirectories(dataDirectory)
        Files.createDirectories(backupDirectory)
    }

    companion object {
        private const val APP_FOLDER_NAME = "Fiscal"
        private const val DATABASE_FILE_NAME = "fiscal.db"
        private const val BACKUP_FOLDER_NAME = "backup"

        private fun defaultDataDirectory(): Path {
            val appData = System.getenv("APPDATA")
            val base = if (appData.isNullOrBlank()) Path.of(System.getProperty("user.home")) else Path.of(appData)
            return base.resolve(APP_FOLDER_NAME)
        }
    }
}
