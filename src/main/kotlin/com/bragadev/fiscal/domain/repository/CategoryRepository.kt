package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.DocumentCategory

interface CategoryRepository {
    suspend fun getCategories(): List<DocumentCategory>
}
