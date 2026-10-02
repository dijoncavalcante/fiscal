package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import java.nio.file.Path

sealed interface RootFolderStatus {
    data class Ready(val path: Path) : RootFolderStatus

    /** A pasta configurada não está acessível (ex.: pendrive desconectado). */
    data class Missing(val path: Path) : RootFolderStatus

    data object NotConfigured : RootFolderStatus
}

/**
 * Determina a pasta raiz ao iniciar:
 * usa a pasta salva; na primeira execução, usa a pasta sugerida somente se ela existir.
 */
class ResolveRootFolderUseCase(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(): RootFolderStatus {
        val settings = settingsRepository.load()
        val saved = settings.rootPath
        if (saved != null) {
            return if (fileRepository.isDirectory(saved)) RootFolderStatus.Ready(saved) else RootFolderStatus.Missing(saved)
        }
        val suggested = settingsRepository.suggestedRootPath
        if (!fileRepository.isDirectory(suggested)) return RootFolderStatus.NotConfigured
        settingsRepository.save(settings.copy(rootPath = suggested))
        return RootFolderStatus.Ready(suggested)
    }
}

class ChangeRootFolderUseCase(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(path: Path): Outcome<Path> {
        if (!fileRepository.isDirectory(path)) return Outcome.Failure(FileOperationError.RootNotFound)
        settingsRepository.save(settingsRepository.load().copy(rootPath = path))
        return Outcome.Success(path)
    }
}
