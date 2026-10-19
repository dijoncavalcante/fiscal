package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.StoredExpenseCheck
import java.nio.file.Path

/** Conferências de despesas já feitas, para não repetir o OCR enquanto o arquivo não mudar. */
interface ExpenseCheckRepository {
    /** Conferências dos arquivos da pasta, por nome de arquivo em minúsculas. */
    suspend fun checksIn(folder: Path): Map<String, StoredExpenseCheck>

    suspend fun save(file: Path, check: StoredExpenseCheck)
}
