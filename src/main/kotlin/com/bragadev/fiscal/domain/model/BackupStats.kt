package com.bragadev.fiscal.domain.model

/** Arquivos guardados ao substituir documentos (permitem desfazer uma substituição). */
data class BackupStats(val fileCount: Int, val totalBytes: Long)
