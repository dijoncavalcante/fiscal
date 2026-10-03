package com.bragadev.fiscal.data.repository

import com.bragadev.fiscal.data.database.FileFlagDao
import com.bragadev.fiscal.domain.repository.FileFlagRepository
import java.nio.file.Path

class FileFlagRepositoryImpl(private val flagDao: FileFlagDao) : FileFlagRepository {
    override suspend fun flagsIn(folder: Path): Map<String, String> = flagDao.notesIn(folderKey(folder))

    override suspend fun set(file: Path, note: String) = flagDao.upsert(folderKey(file.parent), nameKey(file), note)

    override suspend fun clear(file: Path) = flagDao.delete(folderKey(file.parent), nameKey(file))

    override suspend fun move(from: Path, to: Path) =
        flagDao.move(folderKey(from.parent), nameKey(from), folderKey(to.parent), nameKey(to))

    private fun folderKey(folder: Path): String = folder.toAbsolutePath().normalize().toString().lowercase()

    private fun nameKey(file: Path): String = file.fileName.toString().lowercase()
}
