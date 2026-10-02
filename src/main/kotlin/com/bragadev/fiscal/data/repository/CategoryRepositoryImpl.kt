package com.bragadev.fiscal.data.repository

import com.bragadev.fiscal.data.database.CategoryDao
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.rules.DefaultCategories
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CategoryRepositoryImpl(private val categoryDao: CategoryDao) : CategoryRepository {
    private val seedMutex = Mutex()
    private var seeded = false

    override suspend fun getCategories(): List<DocumentCategory> {
        ensureDefaultsSeeded()
        return categoryDao.getAll()
    }

    private suspend fun ensureDefaultsSeeded() = seedMutex.withLock {
        if (seeded) return@withLock
        categoryDao.upsert(DefaultCategories.all)
        seeded = true
    }
}
