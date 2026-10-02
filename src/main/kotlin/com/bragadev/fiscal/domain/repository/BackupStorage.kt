package com.bragadev.fiscal.domain.repository

import java.nio.file.Path

/** Local onde ficam guardados os arquivos substituídos, para permitir desfazer. */
interface BackupStorage {
    fun newBackupPath(fileName: String): Path
}
