package com.bragadev.fiscal.domain.repository

import java.nio.file.Path

/** Locais onde ficam guardados os arquivos substituídos e os excluídos, para permitir desfazer. */
interface BackupStorage {
    fun newBackupPath(fileName: String): Path

    /** Caminho novo na lixeira do FISCAL, para onde vão os arquivos excluídos (o Desfazer os traz de volta). */
    fun newTrashPath(fileName: String): Path
}
