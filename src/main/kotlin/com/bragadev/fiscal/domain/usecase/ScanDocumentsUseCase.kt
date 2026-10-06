package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.map
import com.bragadev.fiscal.domain.repository.FileRepository
import java.nio.file.Path

/** Lista os PDFs e as imagens (JPEG/PNG) de uma pasta, sem entrar nas subpastas. */
class ScanDocumentsUseCase(private val fileRepository: FileRepository) {
    suspend operator fun invoke(folder: Path?): Outcome<List<Document>> {
        if (folder == null) return Outcome.Failure(FileOperationError.FolderNotSelected)
        if (!fileRepository.isDirectory(folder)) return Outcome.Failure(FileOperationError.FolderNotFound)
        return fileRepository.listDocuments(folder).map { documents -> documents.sortedBy { it.name.lowercase() } }
    }
}
