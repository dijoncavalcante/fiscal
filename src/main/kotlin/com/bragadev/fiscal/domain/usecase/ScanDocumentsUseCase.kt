package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.map
import com.bragadev.fiscal.domain.repository.FileRepository
import java.nio.file.Path

class ScanDocumentsUseCase(private val fileRepository: FileRepository) {
    suspend operator fun invoke(root: Path?): Outcome<List<Document>> {
        if (root == null) return Outcome.Failure(FileOperationError.RootNotConfigured)
        if (!fileRepository.isDirectory(root)) return Outcome.Failure(FileOperationError.RootNotFound)
        return fileRepository.listPdfFiles(root).map { documents ->
            documents.sortedWith(compareBy({ it.path.parent?.toString()?.lowercase() }, { it.name.lowercase() }))
        }
    }
}
