package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.MonthFolderInfo
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import java.nio.file.Path
import java.time.YearMonth

/**
 * Protege meses já fechados: nada anterior a [firstEditableMonth] pode ser alterado,
 * nem como destino, nem como origem de um arquivo.
 */
class EditablePeriodPolicy(val firstEditableMonth: YearMonth = FIRST_EDITABLE_MONTH) {

    fun describe(folder: Path): MonthFolderInfo {
        val detected = MonthFolderParser.detectMonth(folder)
        val status = when {
            detected == null -> MonthFolderStatus.UNKNOWN_MONTH
            detected.month < firstEditableMonth -> MonthFolderStatus.LOCKED
            else -> MonthFolderStatus.EDITABLE
        }
        return MonthFolderInfo(folder, detected, MonthFolderParser.detectAccount(folder), status)
    }

    /** Destino precisa ser um mês identificado e liberado. */
    fun checkDestination(folder: Path): FileOperationError? {
        val info = describe(folder)
        return when (info.status) {
            MonthFolderStatus.EDITABLE -> null
            MonthFolderStatus.UNKNOWN_MONTH -> FileOperationError.MonthNotIdentified
            MonthFolderStatus.LOCKED -> FileOperationError.MonthLocked(info.detectedMonth!!.month, firstEditableMonth)
        }
    }

    /** Origem fora de qualquer mês (ex.: Downloads) é permitida; origem em mês bloqueado não. */
    fun checkSource(folder: Path): FileOperationError? {
        val info = describe(folder)
        return if (info.status == MonthFolderStatus.LOCKED) {
            FileOperationError.MonthLocked(info.detectedMonth!!.month, firstEditableMonth)
        } else {
            null
        }
    }

    companion object {
        val FIRST_EDITABLE_MONTH: YearMonth = YearMonth.of(2026, 6)
    }
}
