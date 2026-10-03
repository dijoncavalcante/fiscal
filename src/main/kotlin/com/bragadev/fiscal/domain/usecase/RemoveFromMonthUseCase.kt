package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.flatMap
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.rules.DuplicateNameResolver
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.FileNameRules
import java.nio.file.Path

/**
 * Retira da pasta do mês um arquivo colocado por engano, devolvendo-o para a pasta de origem (esquerda).
 * Nada é apagado: o arquivo só muda de pasta, a operação entra no histórico e pode ser desfeita.
 * Se já existir um arquivo com o mesmo nome na pasta de origem, recebe "(2)", "(3)"...
 */
class RemoveFromMonthUseCase(
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository,
    private val mover: RecordedFileMover,
    private val periodPolicy: EditablePeriodPolicy,
) {
    /** Calcula para onde o arquivo vai, sem alterar nada. */
    suspend fun plan(file: Path): Outcome<Path> {
        if (!fileRepository.exists(file)) return Outcome.Failure(FileOperationError.FileNotFound)
        periodPolicy.checkSource(file.parent)?.let { return Outcome.Failure(it) }

        val destination = settingsRepository.settings.value.sourceFolder
            ?: return Outcome.Failure(FileOperationError.FolderNotSelected)
        if (destination.normalize() == file.parent.normalize()) {
            return Outcome.Failure(FileOperationError.ReturnFolderIsMonthFolder)
        }
        if (!fileRepository.isDirectory(destination)) return Outcome.Failure(FileOperationError.FolderNotFound)
        periodPolicy.checkSource(destination)?.let { return Outcome.Failure(it) }

        val name = file.fileName.toString()
        val existing = fileRepository.listFileNames(destination)
        val finalName = if (FileNameRules.containsIgnoringCase(existing, name)) {
            DuplicateNameResolver.nextNumberedCopy(name, existing)
        } else {
            name
        }
        return Outcome.Success(destination.resolve(finalName))
    }

    suspend operator fun invoke(file: Path): Outcome<FileOperation> =
        plan(file).flatMap { target -> mover.move(file, target, categoryId = null) }
}
