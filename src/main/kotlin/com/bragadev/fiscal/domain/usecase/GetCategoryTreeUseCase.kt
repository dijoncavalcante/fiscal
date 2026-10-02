package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.rules.CategoryHierarchy

class GetCategoryTreeUseCase(private val categoryRepository: CategoryRepository) {
    suspend operator fun invoke(): List<AccountGroup> =
        CategoryHierarchy(categoryRepository.getCategories()).tree()
}
