package com.bragadev.fiscal.domain.model

import java.nio.file.Path
import java.time.YearMonth

/** Mês identificado a partir do caminho de uma pasta e o nome da pasta que o representa. */
data class DetectedMonth(
    val month: YearMonth,
    val folderName: String,
)

enum class MonthFolderStatus {
    /** Mês identificado e liberado para edição. */
    EDITABLE,

    /** Mês anterior ao primeiro mês editável: somente leitura. */
    LOCKED,

    /** A pasta não corresponde a um mês reconhecível. */
    UNKNOWN_MONTH,
}

/** Pasta do mês em edição, com o mês e a conta identificados pelo caminho. */
data class MonthFolderInfo(
    val path: Path,
    val detectedMonth: DetectedMonth?,
    val account: AccountType?,
    val status: MonthFolderStatus,
) {
    val isEditable: Boolean get() = status == MonthFolderStatus.EDITABLE
}
