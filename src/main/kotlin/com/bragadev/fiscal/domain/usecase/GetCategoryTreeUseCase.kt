package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.AccountGroup
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.rules.CategoryHierarchy

class GetCategoryTreeUseCase(private val categoryRepository: CategoryRepository) {
    /** Com [account] informado, retorna só as categorias daquela conta e "Outros". */
    suspend operator fun invoke(account: AccountType? = null): List<AccountGroup> =
        CategoryHierarchy(categoryRepository.getCategories()).tree(account)
}
