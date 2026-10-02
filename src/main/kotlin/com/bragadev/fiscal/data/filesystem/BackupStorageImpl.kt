package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.domain.repository.BackupStorage
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class BackupStorageImpl(private val directories: AppDirectories) : BackupStorage {
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    override fun newBackupPath(fileName: String): Path {
        val uniquePrefix = "${LocalDateTime.now().format(timestampFormat)}-${UUID.randomUUID().toString().take(8)}"
        return directories.backupDirectory.resolve("$uniquePrefix-$fileName")
    }
}
