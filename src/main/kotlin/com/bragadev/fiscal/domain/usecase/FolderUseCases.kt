package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.MonthFolderInfo
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import java.nio.file.Path

/**
 * Carrega as configurações ao iniciar. Na primeira execução, usa a pasta sugerida
 * como pasta de origem somente se ela existir.
 */
class LoadInitialFoldersUseCase(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke() {
        val settings = settingsRepository.load()
        if (settings.sourceFolder != null) return
        val suggested = settingsRepository.suggestedSourceFolder
        if (fileRepository.isDirectory(suggested)) settingsRepository.save(settings.copy(sourceFolder = suggested))
    }
}

/** Troca a pasta exibida à esquerda. */
class ChangeSourceFolderUseCase(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(path: Path): Outcome<Path> {
        if (!fileRepository.isDirectory(path)) return Outcome.Failure(FileOperationError.FolderNotFound)
        settingsRepository.save(settingsRepository.load().copy(sourceFolder = path))
        return Outcome.Success(path)
    }
}

/**
 * Troca a pasta do mês em edição. Meses bloqueados podem ser selecionados para consulta,
 * mas continuam protegidos contra alterações.
 */
class ChangeMonthFolderUseCase(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
    private val policy: EditablePeriodPolicy,
) {
    suspend operator fun invoke(path: Path): Outcome<MonthFolderInfo> {
        if (!fileRepository.isDirectory(path)) return Outcome.Failure(FileOperationError.FolderNotFound)
        settingsRepository.save(settingsRepository.load().copy(monthFolder = path))
        return Outcome.Success(policy.describe(path))
    }
}

/** Descreve a pasta do mês: mês, conta e se pode ser editada. */
class DescribeMonthFolderUseCase(private val policy: EditablePeriodPolicy) {
    operator fun invoke(path: Path): MonthFolderInfo = policy.describe(path)
}
