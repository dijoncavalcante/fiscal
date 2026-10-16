package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.OperationType
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.flatMap
import com.bragadev.fiscal.domain.repository.BackupStorage
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.rules.FileNameRules
import java.nio.file.Path

/**
 * Renomeia um documento da lista da esquerda (pasta de origem), sem tirá-lo da pasta.
 * A extensão é mantida (uma imagem continua imagem) e nunca substitui outro arquivo.
 * Entra no histórico e pode ser desfeito.
 */
class RenameDocumentUseCase(
    private val fileRepository: FileRepository,
    private val mover: RecordedFileMover,
    private val periodPolicy: EditablePeriodPolicy,
) {
    /** Calcula o novo caminho, sem alterar nada. [typedName] é o nome digitado (com ou sem a extensão). */
    suspend fun plan(file: Path, typedName: String): Outcome<Path> {
        if (!fileRepository.exists(file)) return Outcome.Failure(FileOperationError.FileNotFound)
        periodPolicy.checkSource(file.parent)?.let { return Outcome.Failure(it) }

        val currentName = file.fileName.toString()
        val newName = FileNameRules.keepingExtension(currentName, typedName)
        if (newName.isEmpty()) return Outcome.Failure(FileOperationError.NameRequired)
        if (!FileNameRules.isValid(newName)) return Outcome.Failure(FileOperationError.InvalidFileName)
        if (newName == currentName) return Outcome.Failure(FileOperationError.AlreadyInPlace)

        // Mudar só maiúsculas/minúsculas não conflita com o próprio arquivo.
        val others = fileRepository.listFileNames(file.parent).filterNot { it.equals(currentName, ignoreCase = true) }.toSet()
        if (FileNameRules.containsIgnoringCase(others, newName)) return Outcome.Failure(FileOperationError.DestinationAlreadyExists)
        return Outcome.Success(file.resolveSibling(newName))
    }

    suspend operator fun invoke(file: Path, typedName: String): Outcome<FileOperation> =
        plan(file, typedName).flatMap { target -> mover.move(file, target, categoryId = null) }
}

/**
 * "Excluir" um documento: nada é apagado. O arquivo vai para a lixeira do FISCAL (%APPDATA%\Fiscal\lixeira),
 * a operação entra no histórico e o Desfazer o devolve para o mesmo lugar, com o mesmo nome.
 * A Lixeira do Windows não é usada porque, em pendrive, ela apaga o arquivo de vez.
 */
class DeleteDocumentUseCase(
    private val fileRepository: FileRepository,
    private val backupStorage: BackupStorage,
    private val mover: RecordedFileMover,
    private val periodPolicy: EditablePeriodPolicy,
) {
    /** Confere se pode excluir, sem alterar nada (`null` = pode). */
    suspend fun check(file: Path): FileOperationError? {
        if (!fileRepository.exists(file)) return FileOperationError.FileNotFound
        return periodPolicy.checkSource(file.parent)
    }

    suspend operator fun invoke(file: Path): Outcome<FileOperation> {
        check(file)?.let { return Outcome.Failure(it) }
        val target = backupStorage.newTrashPath(file.fileName.toString())
        return mover.move(file, target, categoryId = null, type = OperationType.DELETE)
    }
}
