package com.bragadev.fiscal.data.filesystem

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.domain.repository.BackupStorage
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class BackupStorageImpl(private val directories: AppDirectories) : BackupStorage {
    override fun newBackupPath(fileName: String): Path = directories.backupDirectory.resolve(stamped(fileName))

    override fun newTrashPath(fileName: String): Path = directories.trashDirectory.resolve(stamped(fileName))

    /** "20261016-143000-1a2b3c4d-Nome.pdf": a data no começo diz quando o arquivo foi guardado (usada na limpeza). */
    private fun stamped(fileName: String): String =
        "${LocalDateTime.now().format(TIMESTAMP_FORMAT)}-${UUID.randomUUID().toString().take(8)}-$fileName"

    companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
