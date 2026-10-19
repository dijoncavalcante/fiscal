package com.bragadev.fiscal.data.repository

import com.bragadev.fiscal.data.database.ExpenseCheckDao
import com.bragadev.fiscal.domain.model.StoredExpenseCheck
import com.bragadev.fiscal.domain.repository.ExpenseCheckRepository
import java.nio.file.Path

class ExpenseCheckRepositoryImpl(private val dao: ExpenseCheckDao) : ExpenseCheckRepository {
    override suspend fun checksIn(folder: Path): Map<String, StoredExpenseCheck> = dao.checksIn(folderKey(folder))

    override suspend fun save(file: Path, check: StoredExpenseCheck) =
        dao.upsert(folderKey(file.parent), file.fileName.toString().lowercase(), check)

    private fun folderKey(folder: Path): String = folder.toAbsolutePath().normalize().toString().lowercase()
}
