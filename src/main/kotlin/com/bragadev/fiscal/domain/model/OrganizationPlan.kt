package com.bragadev.fiscal.domain.model

import java.nio.file.Path

enum class OrganizeMode {
    /** Renomeia mantendo o arquivo na pasta atual. */
    RENAME_ONLY,

    /** Renomeia e move para a pasta da categoria. */
    RENAME_AND_MOVE,
}

/**
 * Proposta calculada antes de qualquer alteração em disco.
 * Nada é alterado até que a proposta seja executada.
 */
data class OrganizationPlan(
    val source: Path,
    val category: DocumentCategory,
    val mode: OrganizeMode,
    val targetDirectory: Path,
    val suggestedName: String,
    val hasConflict: Boolean,
    val numberedCopyName: String,
) {
    val targetPath: Path get() = targetDirectory.resolve(suggestedName)
    val numberedCopyPath: Path get() = targetDirectory.resolve(numberedCopyName)
    val currentName: String get() = source.fileName.toString()
}
